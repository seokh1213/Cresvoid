package lab

import org.apache.spark.sql.SparkSession

fun installFixtures(spark: SparkSession) {
    val inputs = mapOf(
        "sample_lhs" to "VALUES ('a',1),('b',1),('c',2),('d',NULL),('e',NULL),('f',-1),('g',3) AS t(id,k)",
        "sample_rhs" to "VALUES ('u',1),('v',1),('w',NULL),('x',NULL),('y',-1) AS t(id,k)",
        "sample_blocked" to "VALUES (2),(CAST(NULL AS INT)) AS t(k)",
        "alternate_lhs" to "VALUES ('a',2),('b',NULL),('c',-1),('d',2),('e',4) AS t(id,k)",
        "alternate_rhs" to "VALUES ('u',2),('v',2),('w',NULL),('x',-1),('y',-1) AS t(id,k)",
        "alternate_blocked" to "VALUES (-1) AS t(k)",
        "sample_first" to "VALUES ('ann','east'),('ann','east'),('bob','west') AS t(user,region)",
        "sample_second" to "VALUES ('south','cy'),('east','ann') AS t(region,user)",
        "alternate_first" to "VALUES ('dee','north') AS t(user,region)",
        "alternate_second" to "VALUES ('north','dee'),('west','eli') AS t(region,user)",
        "sample_numbers" to "VALUES ('t1','7',1),('t2','oops',2147483647),('t3',NULL,CAST(NULL AS INT)),('t4','2147483648',-2),('t5','-2',4) AS t(id,raw,n)",
        "alternate_numbers" to "VALUES ('t1','-8',CAST(-2147483648 AS INT)),('t2',NULL,0),('t3','bad',2147483647),('t4','0',CAST(NULL AS INT)) AS t(id,raw,n)",
    )
    inputs.forEach { (name, values) ->
        spark.sql("SELECT * FROM $values").createOrReplaceTempView(name)
    }
}
