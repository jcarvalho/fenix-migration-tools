
def file = new File("src/main/resources/services.xml")

def list = new File("../autos").text

def xml = new XmlSlurper().parse(file)

def services = new HashMap()

xml.service.each { service ->

    if(list.contains(service.name.text())) {
        services.put("value=\"" + service.name.text(), "value=\"" + service.implementationClass.text())
    }
}


services.put("value=\"SearchEventEditionByMultiLanguageString", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchEventEditionByMultiLanguageString")
services.put("value=\"SearchResearchEvent", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchResearchEvent")
services.put("value=\"SearchTeachersByName", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchTeachersByName")
services.put("value=\"SearchGrantCostCenters", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchGrantCostCenters")
services.put("value=\"SearchScientificJournals", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchScientificJournals")
services.put("value=\"SearchPartySocialSecurityNumber", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchPartySocialSecurityNumber")
services.put("value=\"SearchCountry", "value=\"net.sourceforge.fenixedu.applicationTier.Servico.commons.searchers.SearchCountry")


println services

new File("src/main/webapp").eachFileRecurse { jsp ->
    if(jsp.name.endsWith(".jsp") || jsp.name.endsWith(".xml")) {

        def text = jsp.text

        services.each { entry, value ->
            text = text.replace(entry, value)
        }

        jsp.text = text
    }
}
