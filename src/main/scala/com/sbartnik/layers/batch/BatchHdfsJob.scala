package com.sbartnik.layers.batch

import com.sbartnik.common.CassandraOperations
import com.sbartnik.config.AppConfig
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.{SQLContext, SaveMode, SparkSession}

object BatchHdfsJob extends App {

  val conf = AppConfig
  //val batchImagesPath = conf.hdfsBatchImagesPath

  //val cassandraSession = CassandraOperations.getInitializedSession

  val ss = SparkSession
    .builder()
    .appName("Spark Batch Processing")
    .master(AppConfig.sparkMaster)
    .config("spark.sql.warehouse.dir", "file:///${system:user.dir}/spark-warehouse")
    .config("spark.casandra.connection.host", conf.Cassandra.host)
    .config("spark.casandra.connection.port", conf.Cassandra.port)
    .config("spark.cassandra.auth.username", conf.Cassandra.userName)
    .getOrCreate()

  val sc = ss.sparkContext
  val sqlc = ss.sqlContext

  val dfToProcess = sqlc.read.parquet(conf.hdfsDataPath)
      //.where("unix_timestamp() - timestampBucket / 1000 <= 60 * 60 * 1")

  dfToProcess.createOrReplaceTempView("records")

  //////////////////////////
  // Compute unique visitors
  //////////////////////////

  val uniqueVisitorsBySite = sqlc.sql(
    """SELECT site, timestampBucket as timestamp_bucket,
        |COUNT(DISTINCT visitor) as unique_visitors
      |FROM records
      |GROUP BY site, timestampBucket
    """.stripMargin)

  //uniqueVisitorsBySite.show(500)

//  uniqueVisitorsBySite
//    .write
//    .mode(SaveMode.Append)
//    .partitionBy("timestampBucket")
//    .parquet(batchImagesPath + "/uniqueVisitorsBySite")

  uniqueVisitorsBySite
    .write
    .format("org.apache.spark.sql.cassandra")
    .options(Map("keyspace" -> conf.Cassandra.keyspaceName, "table" -> conf.Cassandra.tables.get(0)))
    .save()

  //////////////////////////
  // Compute action by site
  //////////////////////////

  val actionsBySite = sqlc.sql(
    """SELECT site, timestampBucket as timestamp_bucket,
        |SUM(CASE WHEN action = 'add_to_favorites' THEN 1 ELSE 0 END) as fav_count,
        |SUM(CASE WHEN action = 'comment' THEN 1 ELSE 0 END) as comm_count,
        |SUM(CASE WHEN action = 'page_view' THEN 1 ELSE 0 END) as view_count
      |FROM records
      |GROUP BY site, timestampBucket
    """.stripMargin
  ).cache()

  //actionsBySite.show(500)

//  actionsBySite
//    .write
//    .mode(SaveMode.Append)
//    .partitionBy("timestampBucket")
//    .parquet(batchImagesPath + "/actionsBySite")

  actionsBySite
    .write
    .format("org.apache.spark.sql.cassandra")
    .options(Map("keyspace" -> conf.Cassandra.keyspaceName, "table" -> conf.Cassandra.tables.get(1)))
    .save()
}
