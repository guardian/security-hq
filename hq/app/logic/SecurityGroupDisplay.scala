package logic

import model.{ELB, Instance, SGInUse}


object SecurityGroupDisplay {

  case class ResourceIcons(instances: Int, elbs: Int, unknown: Int)

  def resourceIcons(usages: List[SGInUse]): ResourceIcons = {

    val instances = usages.collect {
      case Instance(_) => 1
    }.sum
    val elbs = usages.collect {
      case ELB(_) => 1
    }.sum
    val unknown = usages.size - instances - elbs

    ResourceIcons(instances, elbs, unknown)
  }

}
