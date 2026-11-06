package lab

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

class InsertMeter {
    var singles: Int = 0
    var batches: Int = 0
    var batchRows: Int = 0

    companion object {
        val current = ThreadLocal<InsertMeter>()
    }
}

class MeasuredDataSource(private val source: DataSource) : DataSource by source {
    override fun getConnection(): Connection = measured(source.connection)
    override fun getConnection(user: String?, password: String?): Connection = measured(source.getConnection(user, password))

    private fun measured(connection: Connection): Connection = proxy { method, args ->
        val result = invoke(connection, method, args)
        if (method.name == "prepareStatement" && result is PreparedStatement &&
            (args?.firstOrNull() as? String)?.trimStart()?.startsWith("insert", ignoreCase = true) == true
        ) measuredStatement(result) else result
    }

    private fun measuredStatement(statement: PreparedStatement): PreparedStatement = proxy { method, args ->
        InsertMeter.current.get()?.let { meter ->
            when (method.name) {
                "executeUpdate", "executeLargeUpdate", "execute" -> meter.singles++
                "executeBatch", "executeLargeBatch" -> meter.batches++
                "addBatch" -> meter.batchRows++
            }
        }
        invoke(statement, method, args)
    }

    private inline fun <reified T> proxy(crossinline handler: (Method, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            handler(method, args)
        } as T

    private fun invoke(target: Any, method: Method, args: Array<out Any?>?): Any? = try {
        method.invoke(target, *(args ?: emptyArray()))
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }
}
