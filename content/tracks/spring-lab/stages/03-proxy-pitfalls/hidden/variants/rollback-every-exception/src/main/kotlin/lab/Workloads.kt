package lab

import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionInterceptor
import java.io.IOException

fun <T : Any> transactionProxy(target: T, type: Class<T>, database: Database): T {
    val advice = TransactionInterceptor().apply {
        transactionManager = database.transactions
        transactionAttributeSource = AnnotationTransactionAttributeSource()
        afterPropertiesSet()
    }
    val factory = ProxyFactory(target).apply {
        isProxyTargetClass = true
        addAdvice(advice)
    }
    return type.cast(factory.getProxy())
}

open class SelfCall(private val database: Database) {
    open fun execute() = write()

    @Transactional
    open fun write() {
        database.insert("single")
        throw IllegalStateException("self")
    }
}

open class PrivateCall(private val database: Database) {
    open fun execute() = write()

    @Transactional
    private fun write() {
        database.insert("single")
        throw IllegalStateException("private")
    }
}

final class FinalCall(private val database: Database) {
    @Transactional
    final fun execute() {
        database.insert("single")
        throw IllegalStateException("final")
    }
}

open class CheckedCall(private val database: Database) {
    @Transactional(rollbackFor = [Exception::class])
    open fun execute() {
        database.insert("single")
        throw IOException("checked")
    }
}

open class InnerCall(private val database: Database) {
    @Transactional(propagation = Propagation.REQUIRED)
    open fun required() = fail()

    @Transactional(propagation = Propagation.NESTED)
    open fun nested() = fail()

    private fun fail() {
        database.insert("inner")
        throw IllegalStateException("inner")
    }
}

open class OuterCall(private val database: Database, private val inner: InnerCall) {
    @Transactional
    open fun required() = surround { inner.required() }

    @Transactional
    open fun nested() = surround { inner.nested() }

    private fun surround(invokeInner: () -> Unit) {
        database.insert("before")
        try {
            invokeInner()
        } catch (_: IllegalStateException) {
            // The outer boundary still has to complete its transaction after this catch.
        }
        database.insert("after")
    }
}

fun executeCase(case: String, database: Database) {
    when (case) {
        "self" -> transactionProxy(SelfCall(database), SelfCall::class.java, database).execute()
        "private" -> transactionProxy(PrivateCall(database), PrivateCall::class.java, database).execute()
        "final" -> transactionProxy(FinalCall(database), FinalCall::class.java, database).execute()
        "checked" -> transactionProxy(CheckedCall(database), CheckedCall::class.java, database).execute()
        "required", "nested" -> {
            val inner = transactionProxy(InnerCall(database), InnerCall::class.java, database)
            val outer = transactionProxy(OuterCall(database, inner), OuterCall::class.java, database)
            if (case == "required") outer.required() else outer.nested()
        }
        else -> error("unknown case")
    }
}
