package com.prisma.api.mutations

import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.ApiConnectorCapability.JoinRelationsCapability
import com.prisma.shared.models.Project
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class UpdateManyRelationFilterSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationsCapability)

  val schema =
    """type Top{
      |   id: ID! @unique
      |   top: String!
      |   bottom: Bottom
      |}
      |
      |type Bottom{
      |   id: ID! @unique
      |   bottom: String!
      |   top: Top
      |   veryBottom: VeryBottom
      |}
      |
      |type VeryBottom{
      |   id: ID! @unique
      |   veryBottom: String!
      |   bottom: Bottom
      |}""".stripMargin

  val project: Project = SchemaDsl.fromString() { schema }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
  }

  override def beforeEach(): Unit = database.truncateProjectTables(project)

  "The updateMany Mutation" should "update the items matching the where relation filter" in {
    createTop("top1")
    createTop("top2")

    server.query(
      s"""mutation {
         |  createTop(
         |    data: {
         |      top: "top3"
         |      bottom: {
         |        create: {bottom: "bottom1"}
         |      }
         |    }
         |  ) {
         |    id
         |  }
         |}
      """.stripMargin,
      project
    )

    val filter = """{ bottom: null }"""

    val firstCount       = topUpdatedCount
    val filterQueryCount = server.query(s"""{tops(where: $filter){id}}""", project).pathAsSeq("data.tops").length
    val filterUpdatedCount = server
      .query(s"""mutation {updateManyTops(where: $filter, data: { top: "updated" }){count}}""".stripMargin, project)
      .pathAsLong("data.updateManyTops.count")
    val lastCount = topUpdatedCount

    firstCount should be(0)
    firstCount + filterQueryCount should be(lastCount)
    lastCount - firstCount should be(filterUpdatedCount)
  }

  "The updateMany Mutation" should "update all items if the filter is empty" in {
    createTop("top1")
    createTop("top2")

    server.query(
      s"""mutation {
         |  createTop(
         |    data: {
         |      top: "top3"
         |      bottom: {
         |        create: {bottom: "bottom1"}
         |      }
         |    }
         |  ) {
         |    id
         |  }
         |}
      """.stripMargin,
      project
    )

    val firstCount       = topUpdatedCount
    val filterQueryCount = server.query(s"""{tops{id}}""", project).pathAsSeq("data.tops").length
    val filterUpdatedCount = server
      .query(s"""mutation {updateManyTops(data: { top: "updated" }){count}}""".stripMargin, project)
      .pathAsLong("data.updateManyTops.count")
    val lastCount = topUpdatedCount

    firstCount should be(0)
    firstCount + filterQueryCount should be(lastCount)
    lastCount - firstCount should be(filterUpdatedCount)
  }

  "The updateMany Mutation" should "work for deeply nested filters" in {
    createTop("top1")
    createTop("top2")

    server.query(
      s"""mutation {
         |  createTop(
         |    data: {
         |      top: "top3"
         |      bottom: {
         |        create: {
         |        bottom: "bottom1"
         |        veryBottom: {create: {veryBottom: "veryBottom"}}}
         |      }
         |    }
         |  ) {
         |    id
         |  }
         |}
      """.stripMargin,
      project
    )

    val filter = """{ bottom: {veryBottom: {veryBottom: "veryBottom"}}}"""

    val firstCount       = topUpdatedCount
    val filterQueryCount = server.query(s"""{tops(where: $filter){id}}""", project).pathAsSeq("data.tops").length
    val filterUpdatedCount = server
      .query(s"""mutation {updateManyTops(where: $filter, data: { top: "updated" }){count}}""".stripMargin, project)
      .pathAsLong("data.updateManyTops.count")
    val lastCount = topUpdatedCount

    firstCount should be(0)
    firstCount + filterQueryCount should be(lastCount)
    lastCount - firstCount should be(filterUpdatedCount)
  }

  def topCount: Int        = server.query("{ tops { id } }", project).pathAsSeq("data.tops").size
  def topUpdatedCount: Int = server.query("""{ tops(where: {top: "updated"}) { id } }""", project).pathAsSeq("data.tops").size

  def createTop(top: String): Unit = {
    server.query(
      s"""mutation {
        |  createTop(
        |    data: {
        |      top: "$top"
        |    }
        |  ) {
        |    id
        |  }
        |}
      """.stripMargin,
      project
    )
  }
}
