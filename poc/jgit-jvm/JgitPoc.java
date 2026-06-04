import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * git-reader JGit PoC (pure-JVM).
 *
 * 検証対象:
 *  1. HTTPS(+token) で clone
 *  2. 全ブランチ fetch
 *  3. ブランチを「直近コミット順」に並べる
 *  4. ローカル変更(追跡ファイル改変 + 未追跡ファイル作成)を作り、
 *     fetch -> reset --hard origin/<branch> -> clean -fdx で完全破棄できるか
 *
 * 環境変数:
 *  GIT_URL    (default: https://github.com/octocat/Hello-World.git)
 *  GIT_BRANCH (未指定なら clone 時の既定ブランチ)
 *  GIT_USER   (private 用。public は不要)
 *  GIT_TOKEN  (private 用。public は不要)
 */
public class JgitPoc {

    static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static void main(String[] args) throws Exception {
        String url = env("GIT_URL", "https://github.com/octocat/Hello-World.git");
        String branchEnv = System.getenv("GIT_BRANCH");
        String user = System.getenv("GIT_USER");
        String token = System.getenv("GIT_TOKEN");

        CredentialsProvider cp = null;
        if (token != null && !token.isBlank()) {
            cp = new UsernamePasswordCredentialsProvider(user == null ? "" : user, token);
            System.out.println("[auth] HTTPS + token (user=" + (user == null ? "<empty>" : user) + ")");
        } else {
            System.out.println("[auth] none (public)");
        }

        Path work = Paths.get("work", repoDirName(url));
        Files.createDirectories(work.getParent());

        System.out.println("==============================================");
        System.out.println("URL      : " + url);
        System.out.println("workdir  : " + work.toAbsolutePath());
        System.out.println("==============================================");

        Git git = openOrClone(url, work, cp);
        try (git) {
            Repository repo = git.getRepository();

            // 2. 全ブランチ fetch
            step("fetch (+refs/heads/*)");
            git.fetch()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*"))
                    .setCredentialsProvider(cp)
                    .setRemoveDeletedRefs(true)
                    .call();

            // 3. ブランチ直近順
            step("ブランチ一覧 (直近コミット順)");
            List<BranchInfo> branches = listBranchesByRecency(repo);
            for (BranchInfo b : branches) {
                System.out.printf("   %-30s %s  %.7s%n", b.name, FMT.format(b.when), b.sha);
            }

            String branch = (branchEnv != null && !branchEnv.isBlank())
                    ? branchEnv
                    : defaultBranch(branches, repo);
            System.out.println("対象ブランチ: " + branch);

            // 対象ブランチへ checkout (常に origin に合わせる)
            step("checkout " + branch);
            checkoutForced(git, repo, branch);

            // 4-a. ローカル変更を作る
            step("ローカル変更を作成 (破棄テスト用)");
            String trackedRel = firstTrackedFile(repo);
            Path trackedAbs = work.resolve(trackedRel);
            byte[] original = Files.readAllBytes(trackedAbs);
            Files.writeString(trackedAbs, "\n!!! LOCAL EDIT THAT MUST BE DISCARDED !!!\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            Path untracked = work.resolve("POC_UNTRACKED.tmp");
            Files.writeString(untracked, "junk", StandardCharsets.UTF_8);
            System.out.println("   改変(追跡): " + trackedRel);
            System.out.println("   作成(未追跡): POC_UNTRACKED.tmp");
            System.out.println("   status.isClean = " + git.status().call().isClean() + " (false 期待)");

            // 4-b. 破棄: fetch -> reset --hard -> clean -fdx
            step("破棄: reset --hard origin/" + branch + " + clean -fdx");
            git.reset().setMode(ResetType.HARD).setRef("origin/" + branch).call();
            git.clean().setCleanDirectories(true).setForce(true).setIgnore(false).call();

            // 4-c. 検証
            step("検証");
            boolean trackedRestored = java.util.Arrays.equals(original, Files.readAllBytes(trackedAbs));
            boolean untrackedRemoved = !Files.exists(untracked);
            boolean clean = git.status().call().isClean();
            System.out.println("   追跡ファイル復元      : " + ok(trackedRestored));
            System.out.println("   未追跡ファイル削除    : " + ok(untrackedRemoved));
            System.out.println("   status.isClean        : " + ok(clean));

            // HEAD 情報
            try (RevWalk rw = new RevWalk(repo)) {
                RevCommit head = rw.parseCommit(repo.resolve("HEAD"));
                System.out.printf("   HEAD = %.7s  %s%n",
                        head.getName(), head.getShortMessage());
            }

            boolean pass = trackedRestored && untrackedRemoved && clean;
            System.out.println("==============================================");
            System.out.println(pass ? "RESULT: PASS ✅" : "RESULT: FAIL ❌");
            System.out.println("==============================================");
            if (!pass) System.exit(1);
        }
    }

    static Git openOrClone(String url, Path work, CredentialsProvider cp) throws Exception {
        if (Files.exists(work.resolve(".git"))) {
            step("open (既存clone再利用)");
            return Git.open(work.toFile());
        }
        step("clone");
        return Git.cloneRepository()
                .setURI(url)
                .setDirectory(work.toFile())
                .setCredentialsProvider(cp)
                .call();
    }

    static void checkoutForced(Git git, Repository repo, String branch) throws Exception {
        boolean localExists = repo.findRef("refs/heads/" + branch) != null;
        try {
            git.checkout()
                    .setName(branch)
                    .setForced(true)
                    .setCreateBranch(!localExists)
                    .setStartPoint("origin/" + branch)
                    .call();
        } catch (RefNotFoundException e) {
            // 既定ブランチ名が origin に無いケースのフォールバック
            git.checkout().setName(branch).setForced(true).call();
        }
    }

    static List<BranchInfo> listBranchesByRecency(Repository repo) throws IOException {
        List<BranchInfo> out = new ArrayList<>();
        try (RevWalk rw = new RevWalk(repo)) {
            for (Ref ref : repo.getRefDatabase().getRefsByPrefix("refs/remotes/origin/")) {
                String name = ref.getName().substring("refs/remotes/origin/".length());
                if (name.equals("HEAD")) continue;
                RevCommit c = rw.parseCommit(ref.getObjectId());
                out.add(new BranchInfo(name, c.getName(),
                        c.getCommitterIdent().getWhenAsInstant()));
            }
        }
        out.sort(Comparator.comparing((BranchInfo b) -> b.when).reversed());
        return out;
    }

    static String defaultBranch(List<BranchInfo> branches, Repository repo) throws IOException {
        String head = repo.getBranch();
        for (BranchInfo b : branches) if (b.name.equals(head)) return b.name;
        return branches.isEmpty() ? head : branches.get(0).name;
    }

    static String firstTrackedFile(Repository repo) throws IOException {
        try (RevWalk rw = new RevWalk(repo)) {
            RevCommit head = rw.parseCommit(repo.resolve("HEAD"));
            try (TreeWalk tw = new TreeWalk(repo)) {
                tw.addTree(head.getTree());
                tw.setRecursive(true);
                if (tw.next()) return tw.getPathString();
            }
        }
        throw new IllegalStateException("追跡ファイルが見つかりません");
    }

    static String env(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }

    static String repoDirName(String url) {
        String s = url.replaceAll("\\.git$", "");
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : "repo";
    }

    static String ok(boolean b) { return b ? "OK ✅" : "NG ❌"; }

    static void step(String s) { System.out.println("\n--- " + s + " ---"); }

    record BranchInfo(String name, String sha, Instant when) {}
}
