package com.rallyhealth.reactive

import org.joda.time.DateTime
import org.scalatest.FunSuite
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class ReactiveBoxPersistenceSpec extends FunSuite {

  val atMost = Duration(2000, "millis")

  test("Reactive CRUD ops") {

    val cBoxId = BSONObjectID.generate().stringify
    val rBoxId = BSONObjectID.generate().stringify
    val fBoxId = BSONObjectID.generate().stringify
    val bBoxId = BSONObjectID.generate().stringify
    val bInABoxId = BSONObjectID.generate().stringify

    val now = DateTime.now

    val cBox = CorrugatedBox(cBoxId, length = 1, width = 1, height = 1, manufactureDate =  now, lastShipped = Some(now),layers = 4)
    val rBox = RigidBox(rBoxId, length = 4, width = 2, height = 2, manufactureDate =  now, lastShipped = Some(now), numberOfPiece = 5)
    val fBox = FoldingBox(fBoxId, length = 2, width = 1, height = 1, manufactureDate =  now, lastShipped = Some(now), style = "b")
    val boxOfBoxes = BoxOfBoxes(bBoxId, length = 3, width = 1, height = 1, manufactureDate =  now, lastShipped = None, boxes = Set(cBox, rBox, fBox))
    val boxInABox = BoxInABox(bInABoxId, length = 5, width = 1, height = 1, manufactureDate =  now, lastShipped = None, box = cBox)

    val persister = new ReactiveBoxPersistence()

    //remove any previous boxes
    val deletionResult = Await.result(persister.deleteAll(), atMost)
    assert(deletionResult.ok)
    //save boxes

    Await.result(
      Future.sequence(
        Seq(
          persister.save(cBox),
          persister.save(rBox),
          persister.save(fBox),
          persister.save(boxOfBoxes),
          persister.save(boxInABox))), atMost)

    //find corrugatedBox
    val box = Await.result[Option[CorrugatedBox]](persister.findOneCorrugatedBox(), atMost)
    assert(box.isDefined)
    assert(box.get == cBox)

    //find all boxes sorted by length
    val boxes = Await.result[List[Box]](persister.findAllBoxesSortedByLength(), atMost)
    assert(boxes(0).length == 1)
    assert(boxes(1).length == 2)
    assert(boxes(2).length == 3)
    assert(boxes(3).length == 4)
    assert(boxes(4).length == 5)
    //verify nullable value , box(2) has lastShipped None
    assert(boxes(2).lastShipped.isEmpty)
    assert(boxes(3).lastShipped == Some(now))

    val totalLength = Await.result(persister.findAggregateLength(), atMost)
    assert(totalLength == 15)


    val deletionResultAfter = Await.result(persister.deleteAll(), atMost)
    assert(deletionResultAfter.ok)
  }

  test("stress") {
    var id = ""
    var timeBefore = System.currentTimeMillis()
    val persister = new ReactiveBoxPersistence()
    for ( i <- 1 to 100000) {
      id = BSONObjectID.generate().stringify
      val cBox = CorrugatedBox(id, length = 1, width = 1, height = 1, manufactureDate =  DateTime.now, lastShipped = None,layers = 4)
      Await.result(persister.save(cBox), atMost )
      val box = Await.result[Option[CorrugatedBox]](persister.findCorrugatedBoxById(id), atMost)
      assert(box.get == cBox)

    }
    var timeAfter = System.currentTimeMillis()
    println((timeAfter - timeBefore)/(1000))
  }
}
