package logic

import model.{SGInUse, _}
import play.twirl.api.Html

object SecurityGroupService {

  def resourceIcons(usages: Set[SGInUse]): Set[Html] = {

    def iconAndCount(usage: Class[_], count: Int): Html = {
      usage match {
        case i if i == classOf[Instance] => {
          Html(s"""<span class="sg--details__count">$count</span><i class="material-icons tooltipped" data-position="top" data-delay="50" data-tooltip="EC2 instance">computer</i>""")
        }
        case e if e == classOf[ELB] => {
          Html(s"""<span class="sg--details__count">$count</span><i class="material-icons tooltipped" data-position="top" data-delay="50" data-tooltip="Elastic Load Balancer">device_hub</i>""")
        }
        case _ => {
          Html(s"""<span class="sg--details__count">$count</span><i class="material-icons tooltipped" data-position="top" data-delay="50" data-tooltip="Unknown">report_problem</i>""")
        }
      }
    }

    val details = usages.groupBy(_.getClass).mapValues(_.size)
    for {
      (resourceType, count) <- details
    } yield iconAndCount(resourceType, count)

  }.toSet

}
