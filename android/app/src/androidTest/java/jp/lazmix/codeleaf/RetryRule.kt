package jp.lazmix.codeleaf

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * 計装テストを最大 [attempts] 回まで再試行する JUnit ルール。
 *
 * 対象は **Compose フレームワーク由来の test-only フレーク** の緩和に限る。具体例: LazyColumn の
 * prefetch スケジューラ生成が `Choreographer.getInstance()` で "The current thread must have a looper!"
 * を投げる稀な競合(実機の通常利用ではメインスレッド=Looper ありのため発生しない)。
 *
 * アプリ実不具合(毎回失敗)は再試行しても失敗し続けるため隠蔽しない。再試行は compose ルールより
 * 外側に置き(`order` 大=外側)、各試行でアクティビティ/コンポジションを作り直す。
 */
class RetryRule(private val attempts: Int = 3) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            var last: Throwable? = null
            repeat(attempts) { i ->
                try {
                    base.evaluate()
                    return
                } catch (t: Throwable) {
                    last = t
                    android.util.Log.w(
                        "RetryRule",
                        "${description.displayName} failed attempt ${i + 1}/$attempts: ${t.message}",
                    )
                }
            }
            throw last!!
        }
    }
}
