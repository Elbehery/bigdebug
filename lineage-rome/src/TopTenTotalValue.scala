import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.lineage.LineageContext
import org.apache.spark.lineage.LineageContext._


/**
  * Created by filippo on 05/11/15.
  */

object TopTenTotalValue {

  def main(args: Array[String]) {

    val conf = new SparkConf()
    conf.setMaster("local[2]")
    var logFile = "./inputs/City_Of_Trenton_-_2015_Certified_Tax_List.csv"
    conf.setAppName("TopTenTotalValue" + " - " + logFile)

    val sc = new SparkContext(conf)
    var lineage = true
    val lc = new LineageContext(sc)
    lc.setCaptureLineage(lineage)

    // Functions to use
    def verify(id: String, x: Double, y: Double) =
      if (x == y) println("Result Verified")
      else println("ERROR on (" + id + "). value is: " + x + " expected: " + y)
    def splitID(s: String) = s.split(",")
    def splitMon(s: String) = s.split(",\\$")
    def getTot(s: String) = s.split(",").last.replace("$", " ").trim().toDouble

    // Job
    val lines = lc.textFile(logFile)

    val result = lines.map(word => {
      val id = splitID(word)
      val tot = splitMon(word)
      (id(0).concat(" " + id(1)), tot(1).toDouble + tot(2).toDouble)
    })


   val r = result.collect()
    r.foreach(println)


    lc.setCaptureLineage(false)
    Thread.sleep(1000)




    // Lineage
    var linRDD = result.getLineage()
    //linRDD.collect.foreach(println)
    linRDD = linRDD.goBack()
    //linRDD.collect.foreach(println)



    linRDD.show().foreach(
        line => {
            val linID = splitID(line)
            val lineaID = linID(0).concat(" " + linID(1))
            r.foreach(x => {
              if (x._1.equals(lineaID)) verify(x._1, x._2, getTot(line))
            })
    })






  }
}

