package logic

import model.{ELB, Ec2Instance, SGInUse}


object SecurityGroupDisplay {

  case class ResourceIcons(instances: Int, elbs: Int, unknown: Int)

  def resourceIcons(usages: List[SGInUse]): ResourceIcons = {

    val (instances, elbs, unknown) = usages.foldLeft(0,0,0) {
      case ( (ins, elb, unk), Ec2Instance(_) ) => (ins+1, elb, unk)
      case ( (ins, elb, unk), ELB(_) ) => (ins, elb+1, unk)
      case ( (ins, elb, unk), _ ) => (ins, elb, unk+1)
    }

    ResourceIcons(instances, elbs, unknown)
  }

}
