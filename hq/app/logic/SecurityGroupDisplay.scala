package logic

import model.{EfsVolume, ELB, Ec2Instance, SGInUse, SGOpenPortsDetail}


object SecurityGroupDisplay {

  case class ResourceIcons(instances: Int, elbs: Int, unknown: Int, efss: Int)
  case class SGReportSummary(total: Int, suppressed: Int, flagged: Int, active: Int)

  case class SGReportDisplay(
    suppressed: List[(SGOpenPortsDetail, Set[SGInUse])],
    flagged: List[(SGOpenPortsDetail, Set[SGInUse])]
  )

  def resourceIcons(usages: List[SGInUse]): ResourceIcons = {

    val (instances, elbs, unknown, efss) = usages.foldLeft(0,0,0,0) {
      case ( (ins, elb, unk, efs), Ec2Instance(_) ) => (ins+1, elb, unk, efs)
      case ( (ins, elb, unk, efs), ELB(_) ) => (ins, elb+1, unk, efs)
      case ( (ins, elb, unk, efs), EfsVolume(_) ) => (ins, elb, unk, efs+1)
      case ( (ins, elb, unk, efs), _ ) => (ins, elb, unk+1, efs)
    }
    ResourceIcons(instances, elbs, unknown, efss)
  }

  def reportSummary(sgs: List[(SGOpenPortsDetail, Set[SGInUse])]): SGReportSummary = {
    val (active, _) = sgs.partition( sg => sg._2.nonEmpty )
    val (suppressed, flagged) = sgs.partition( sg => sg._1.isSuppressed )

    SGReportSummary(sgs.length, suppressed.length, flagged.length, active.length)
  }

  def hasSuppressedReports(sgs: List[(SGOpenPortsDetail, Set[SGInUse])]): Boolean = {
    sgs.exists(sg => sg._1.isSuppressed)
  }

  def splitReportsBySuppressed(sgs: List[(SGOpenPortsDetail, Set[SGInUse])]): SGReportDisplay = {
    val (suppressed, flagged) = sgs.partition( sg => sg._1.isSuppressed )

    SGReportDisplay(suppressed, flagged)
  }

}
