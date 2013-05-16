
class Chain {
    def name
    def filters

    String toString() {
        "Chain name: " + name + ""
    }
}

class Service {
    def name
    def implementation
    def arguments
    def chains
    def returnType
    def exceptions

    String toString() {
        "Service name: " + name + ". Arguments: " + arguments
    }

    def argumentsList() {
        def args = ""
        arguments.each {
            if(args.length() > 0)
                args <<= ", "
            args <<= it
        }
        args
    }

    def argumentNames() {
        def args = ""
        arguments.each {
            if(args.length() > 0)
                args <<= ", "
            if(it.split(" ").length != 2) {
                println "FAIL! " + name + ": " + it
            } else {
                args <<= it.split(" ")[1]
            }
        }
        args
    }
}

def filterMap = new HashMap()
def chainSet = new HashMap()
def serviceSet = new HashMap()

def services = new XmlSlurper().parse(new File("src/main/resources/services.xml"))
def chains = new XmlSlurper().parse(new File("src/main/resources/filterChains.xml"))
def filters = new XmlSlurper().parse(new File("src/main/resources/filters.xml"))

filters.filter.each { filter ->
    filterMap.put(filter.name.text(), filter.implementationClass.text())
}

chains.filterChain.each { chain ->
    def c = new Chain()
    c.name = chain.name.text()

    def expression = chain.expression.text().replace(' ', '').replace('\n', '').split("\\|\\|")

    c.filters = expression
    chainSet.put(c.name, c)
}

services.service.each { service ->
    def s = new Service()

    s.name = service.name.text()
    s.implementation = service.implementationClass.text()
    s.chains = new HashSet()
    s.arguments = new ArrayList()

    def chainName = service.filterChains.chain.@name.text().trim()

    if(chainName != null && !chainName.isEmpty())
        s.chains.add(chainName)

    serviceSet.put(s.name, s)
}

serviceSet.each { name, service ->

    def file = new File("src/main/java/" + service.implementation.replace('.', '/') + ".java")

    def packs = service.implementation.split('\\.')

    def fileName = packs[packs.length - 1]

    def text = file.text

    def first = false

    text.replaceAll("([A-Za-z\\[\\]<>]*)[ \t\n]*run\\(([A-Za-z, \t\n\\[\\]<>]*)\\)[ \t\n]*(throws)?[ \t\n]*([A-Za-z, \t\n]*)\\{", { stuff ->
        if(first)
            return

        first = true
        service.returnType = stuff[1]
        def arguments =  stuff[2].replace('\n', ' ').replace('\t', ' ').replace("final", "")

        service.exceptions = stuff[4]

        arguments.split(",").each { argument ->
            if(argument.trim().length() > 0)
                service.arguments.add(argument.trim())
        }
    })


    text = text.replaceAll("public[ \t\n]*([A-Za-z]*)[ \t\n]*run", { stuff ->
        "protected " + stuff[1] + " run"
    } )

    text = text.substring(0, text.lastIndexOf('}'))

    text = text + generateRunWithACL(service, fileName) + "\n}"

    def splitPoint = text.indexOf("import")

    text = text.substring(0, splitPoint) + "import pt.ist.fenixWebFramework.services.Service;\n" + text.substring(splitPoint)

    file.text = text
}

def generateRunWithACL(service, fileName) {

    def nameSplit = service.name.split("\\.")

    def method = ""

    method <<= "    @Service\n"
    method <<= "    public static " + service.returnType + " run" + nameSplit[nameSplit.length - 1]+ "WithAccessControl(" + service.argumentsList() + ") "
    if(service.exceptions != null && !service.exceptions.trim().isEmpty()) {
        method <<= "throws " + service.exceptions + " "
    }
    method <<= "{\n        "
    if(!"void".equals(service.returnType)) {
        method <<= "return ";
    }
    method <<= "new " + fileName + "().run(" + service.argumentNames() + ");\n"
    method <<= "    }\n"
}
