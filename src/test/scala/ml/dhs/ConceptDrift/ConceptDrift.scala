package ml.dhs.ModelMonitor

import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkContext}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import com.holdenkarau.spark.testing.{DataFrameSuiteBase, SharedSparkContext}

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.jackson.Serialization.{read, write}

object CreateDataTests {
    def create_train_dataset(sc:SparkContext, sqlCtx:SQLContext):DataFrame={
        //val sqlContext = new SQLContext(sc)
        import sqlCtx.implicits._
        return sc.parallelize(Seq(
            ("Closed with non-monetary relief","Branch", 2.0),
            ("Closed with monetary relief","Customer Meeting", 2.4),
            ("Closed with explanation","Branch", 1.4),
            ("Closed with monetary relief","Branch", -1.2),
            ("Closed with monetary relief","Customer Meeting", 1.2),
            ("Closed with non-monetary relief","Branch", 5.4)
        )).toDF("actioncode", "origin", "exampleNumeric")
    }
    def create_test_dataset(sc:SparkContext, sqlCtx:SQLContext):DataFrame={
        //val sqlContext = new SQLContext(sc)
        import sqlCtx.implicits._
        return sc.parallelize(Seq(
            ("Closed with non-monetary relief","Branch", 2.0),
            ("Closed with monetary relief","Customer Meeting", 2.4),
            ("Closed with explanation","Branch", 1.4),
            ("Closed with monetary relief","Branch", -1.2),
            ("Closed with non-monetary relief","Customer Meeting", 1.2),
            ("Closed with non-monetary relief","Branch", 5.4),
            ("Closed with monetary relief","Customer Meeting", 2.4),
            ("Closed with explanation","Branch", 1.4),
            ("Closed with monetary relief","Branch", -1.2),
            ("Closed with monetary relief","Customer Meeting", 1.2),
            ("Closed with non-monetary relief","Branch", 5.4)
        )).toDF("actioncode", "origin", "exampleNumeric")
    }
}

class ComputeBreaksTest extends FunSuite   {
    test("min and max inclusive") {
        val expected=Array(Double.NegativeInfinity, 2.0, Double.PositiveInfinity)
        val min=1.0
        val max=3.0
        val numBins=2
        val results=ConceptDrift.computeBreaks(min, max, numBins)
        assert(expected.length === results.length)
        for ((e, r) <- expected.zip(results)){
            assert(e === r)
        }
    }
    test("returns one more break than bins") {
        val min=1.0
        val max=3.0
        val numBins=2
        val results=ConceptDrift.computeBreaks(min, max, numBins)
        assert(numBins+1 === results.length)
    }
}
class HellingerNumericalTest extends FunSuite {
    test("returns correct value"){
        val prevDist=Array(0.05, 0.15, 0.3, 0.3, 0.15, 0.05)
        val newDist=Array(0.06, 0.15, 0.29, 0.29, 0.15, 0.06)
        val result=ConceptDrift.hellingerNumerical(prevDist, newDist)
        assert(result === 0.02324307098603245)
    }
}

class InitialElementIfNoNumericTest extends FunSuite {
    test("it gets name of first category"){
        val columnNameAnyTypeArray=Array(
            ("hello", ColumnType.Categorical.toString),
            ("world", ColumnType.Categorical.toString)
        )
        val numericArray:Array[String]=Array()
        val result=ConceptDrift.getInitialElementIfNoNumeric(numericArray, columnNameAnyTypeArray)
        val expected=Array("hello")
        for ((e, r) <- expected.zip(result)){
            assert(e === r)
        }
        assert(result.length === 1)
    }
    test("it returns numeric array if it exists"){
        val columnNameAnyTypeArray=Array(
            ("hello", ColumnType.Categorical.toString),
            ("world", ColumnType.Categorical.toString)
        )
        val numericArray:Array[String]=Array("goodbye", "cruel world")
        val result=ConceptDrift.getInitialElementIfNoNumeric(numericArray, columnNameAnyTypeArray)
        for ((e, r) <- numericArray.zip(result)){
            assert(e === r)
        }
        assert(result.length === numericArray.length)
    }
}

class GetNumericColumnsTest extends FunSuite {
    test("it gets only numeric columns"){
        val expected=Array("hello", "world")
        val columnNameAnyTypeArray=Array(
            ("goodbye", ColumnType.Categorical.toString),
            ("hello", ColumnType.Numeric.toString),
            ("world", ColumnType.Numeric.toString)
        ) 
        val result=ConceptDrift.getNamesOfNumericColumns(columnNameAnyTypeArray)
        for ((e, r) <- expected.zip(result)){
            assert(e === r)
        }
    }
}
class ZipperTest extends FunSuite {
    test("it gets all frequency from original list when less from new list"){
        val expected=Array(
            ("hello", 4.0, 0.0), 
            ("goodbye", 5.0, 2.0)
        )
        val l1=Map("hello"->4.0, "goodbye"->5.0)
        val l2=Map("goodbye"->2.0)
        val result=ConceptDrift.zipper(l1, l2)
        for ((e, r) <- expected.zip(result)){
            assert(e._1 === r._1)
            assert(e._2 === r._2)
            assert(e._3 === r._3)
        }
    }
    test("it gets all frequency from new list when origin less than new list"){
        val expected=Array(
            ("hello", 4.0, 3.0), 
            ("goodbye", 5.0, 2.0),
            ("farewell", 0.0, 2.0)
        )
        val l1=Map("hello"->4.0, "goodbye"->5.0)
        val l2=Map("hello"->3.0, "goodbye"->2.0, "farewell"->2.0)
        val result=ConceptDrift.zipper(l1, l2)
        for ((e, r) <- expected.zip(result)){
            assert(e._1 === r._1)
            assert(e._2 === r._2)
            assert(e._3 === r._3)
        }
    }
}

class ComputeMinMaxTest extends FunSuite with DataFrameSuiteBase /*with SharedSparkContext*/{
    test("It returns min and max") {
        val sqlCtx = sqlContext
        import sqlCtx.implicits._
        val trainDataset=CreateDataTests.create_train_dataset(sc, sqlCtx)

        val minMax=ConceptDrift.computeMinMax(trainDataset, Array("exampleNumeric"))
        assert(minMax("min(exampleNumeric)")===(-1.2))
        assert(minMax("max(exampleNumeric)")===5.4)
        assert(minMax("count(exampleNumeric)")===6)
    }
}

class GetNumericDistributionTest extends FunSuite with DataFrameSuiteBase /*with SharedSparkContext*/{
    test("It gets numeric distribution"){
        val sqlCtx = sqlContext
        import sqlCtx.implicits._
        val trainDataset=CreateDataTests.create_train_dataset(sc, sqlCtx)
        val bins=ConceptDrift.computeBreaks(-1.2, 5.4, 3)
        val result=ConceptDrift.getNumericDistribution(
            trainDataset, "exampleNumeric", bins, 6
        )
        val expected=Array(0.16666666666666666, 0.6666666666666666, 0.16666666666666666)
        for ((e, r) <- expected.zip(result)){
            assert(e === r)
        }
    }
}
class GetCategoricalDistributionTest extends FunSuite with DataFrameSuiteBase /*with SharedSparkContext*/{
    test("It gets numeric distribution"){
        val sqlCtx = sqlContext
        import sqlCtx.implicits._
        val trainDataset=CreateDataTests.create_train_dataset(sc, sqlCtx)
        val result=ConceptDrift.getCategoricalDistribution(
            trainDataset, "origin", 6
        )
        val expected=Map(
            "Branch"->0.666666666666666666,
            "Customer Meeting"->0.333333333333333333
        )
        /*for ((e, r) <- expected.zip(result)){
            assert(e("origin") === r("origin"))
            assert(e("frequency") === r("frequency"))
        }*/
        assert(result("Branch")===expected("Branch"))
        assert(result("Customer Meeting")===expected("Customer Meeting"))
    }
}

class GetDistributionsTest extends FunSuite with DataFrameSuiteBase  {
    test("It returns map of distributions") {
        val sqlCtx = sqlContext
        import sqlCtx.implicits._
        val trainDataset=CreateDataTests.create_train_dataset(sc, sqlCtx)
        val columnNameAnyTypeArray=Array(
            ("actioncode", ColumnType.Categorical.toString),
            ("origin", ColumnType.Categorical.toString),
            ("exampleNumeric", ColumnType.Numeric.toString)
        )
        val results=ConceptDrift.getDistributions(
            trainDataset, columnNameAnyTypeArray
        )
        val expected=Map(
            "fields" -> Map(
                "actioncode" -> Map(
                    "distribution" -> Map(
                        "Closed with non-monetary relief"->0.3333333333333333,
                        "Closed with monetary relief"->0.5,
                        "Closed with explanation"->0.16666666666666666
                    ),                    
                    "type" -> ColumnType.Categorical.toString
                ),
                "origin" -> Map(
                    "distribution" -> Map(
                        "Branch"->0.6666666666666666,
                        "Customer Meeting"->0.3333333333333333
                    ),
                    "type" -> ColumnType.Categorical.toString
                ),
                "exampleNumeric" -> Map(
                    "distribution" -> Array(
                        0.16666666666666666,
                        0.6666666666666666,
                        0.16666666666666666
                    ),
                    "type" -> ColumnType.Numeric.toString
                )
            ),
            "numNumericalBins"->3
        )
        implicit val formats = DefaultFormats
        val expectedJson=write(expected)
        val resultsJson=write(results)
        println(expectedJson)
        println(resultsJson)
        assert(expectedJson === resultsJson)
        //val actioncodedistributionResult=results("fields")("actioncode")("distribution")
       // val actioncodedistributionExpected=expected("fields")("actioncode")("distribution")
        //assert(actioncodedistributionResult("actioncode") === actioncodedistributionExpected("actioncode"))
    }
}