/**
 * Created by shrinidhihudli on 2/10/15.
 *
 * register $PIGMIX_JAR
 * A = load '$HDFS_ROOT/page_views' using org.apache.pig.test.pigmix.udf.PigPerformanceLoader()
 *     as (user, action, timespent, query_term, ip_addr, timestamp, estimated_revenue, page_info, page_links);
 * B = foreach A generate user, estimated_revenue;
 * C = group B by user parallel $PARALLEL;
 * D = foreach C {
 *     E = order B by estimated_revenue;
 *     F = E.estimated_revenue;
 *     generate group, SUM(F);
 * }
 *
 * store D into '$PIGMIX_OUTPUT/L16out';
 *
 */
package org.apache.spark.examples.sparkmix

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import java.util.Properties
import java.io.FileInputStream

import org.apache.spark.lineage.LineageContext

object L16 {
  def run(sc: SparkContext, lc: LineageContext, pigMixPath: String, outputPath: String): Long = {

    val properties: Properties = SparkMixUtils.loadPropertiesFile()

    val pageViewsPath = pigMixPath + "page_views_sorted/"

    var pageViews = sc.textFile(pageViewsPath)

    if (lc != null) {
      lc.setCaptureLineage(true)
      pageViews = lc.textFile(pageViewsPath)
    }
    val start = System.currentTimeMillis()

    val A = pageViews.map(x => (SparkMixUtils.safeSplit(x, "\u0001", 0), SparkMixUtils.safeSplit(x, "\u0001", 1),
      SparkMixUtils.safeSplit(x, "\u0001", 2), SparkMixUtils.safeSplit(x, "\u0001", 3),
      SparkMixUtils.safeSplit(x, "\u0001", 4), SparkMixUtils.safeSplit(x, "\u0001", 5),
      SparkMixUtils.safeSplit(x, "\u0001", 6),
      SparkMixUtils.createMap(SparkMixUtils.safeSplit(x, "\u0001", 7)),
      SparkMixUtils.createBag(SparkMixUtils.safeSplit(x, "\u0001", 8))))

    val B = A.map(x => (x._1, SparkMixUtils.safeDouble(x._7)))

    val C = B.groupByKey(properties.getProperty("PARALLEL").toInt)

    val D = C.map(x => (x._1, x._2.toList.sortBy(identity).sum))

    val end = System.currentTimeMillis()

    if (lc != null)
      lc.setCaptureLineage(false)


    D.saveAsTextFile(outputPath)

    return (end - start)

  }
}
