

def services = new XmlSlurper().parse(new File("src/main/resources/services.xml"))

def serviceMap = new HashMap()

def simplify(className) {
    def nameSplit = className.split("\\.")
    nameSplit[nameSplit.length - 1]
}

services.service.each { service ->

    serviceMap.put(service.name.text(), simplify(service.implementationClass.text()))
}

def javas = new File("src/main/java")

javas.eachFileRecurse { file ->
    if(!file.name.endsWith(".java")) {
        return
    }

    def text = file.text

    text = text.replaceAll("ServiceManagerServiceFactory\\.executeService[ \n]*\\([ \n]*\"([A-Za-z0-9.]*)\"[ \n]*,[\n ]*", { stuff ->
        serviceMap.get(stuff[1]) + ".run" + stuff[1] + "("
    })

    file.text = text
}