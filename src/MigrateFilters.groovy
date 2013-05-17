
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

            def len = it.split(" ").length
            args <<= it.split(" ")[len - 1]
        }
        args
    }
}

class Globals {
    static def filterMap = new HashMap();
    static def chainSet = new HashMap();
    static def serviceSet = new HashMap();
}


def services = new XmlSlurper().parse(new File("src/main/resources/services.xml"))
def chains = new XmlSlurper().parse(new File("src/main/resources/filterChains.xml"))
def filters = new XmlSlurper().parse(new File("src/main/resources/filters.xml"))

filters.filter.each { filter ->
    Globals.filterMap.put(filter.name.text(), filter.implementationClass.text())
}

chains.filterChain.each { chain ->
    def c = new Chain()
    c.name = chain.name.text()

    def expression = chain.expression.text().replace(' ', '').replace('\n', '').split("\\|\\|")

    c.filters = expression
    Globals.chainSet.put(c.name, c)
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

    Globals.serviceSet.put(s.name, s)
}

Globals.serviceSet.each { name, service ->

    def file = new File("src/main/java/" + service.implementation.replace('.', '/') + ".java")

    def packs = service.implementation.split('\\.')

    def fileName = packs[packs.length - 1]

    def text = file.text

    def first = false

    text.replaceAll("([A-Za-z\\[\\]<>]*)[ \t\n]*run\\(([A-Za-z, \t\n\\[\\]<>]*)\\)[ \t\n]*(throws)?[ \t\n]*([A-Za-z, \t\n]*)\\{", { stuff ->
        if(first)
            return

        if(stuff[0].contains("void") && stuff[0].contains("()")) {
            println "Matches!" + stuff[0]
            return
        }

        first = true
        service.returnType = stuff[1]
        def arguments =  stuff[2].replace('\n', ' ').replace('\t', ' ').replace("final", "")

        service.exceptions = stuff[4]

        arguments.split(",").each { argument ->
            if(argument.trim().length() > 0)
                service.arguments.add(argument.trim())
        }
    })

    migrateFilters(service)

    migrateService(service, text, file, fileName)
}

def migrateFilters(service) {

    runForFilters(service, {

        def filterClass = Globals.filterMap.get(it)

        if(filterClass == null) {
            println "Null class for filter " + it
            return
        }

        def file = new File("src/main/java/" + filterClass.replace('.', '/') + ".java")

        def text = file.text

        text = text.replace("Object[] parameters", service.argumentsList()).replace("extends Filtro ", "")

        text = text.replaceAll("parameters\\[([0-9]+)\\]",  { stuff ->
            def index = Integer.parseInt(stuff[1])

            def args = service.argumentNames().toString().split(",")

            if(args.length > index) {
                args[index]
            } else {
                "parameters[" + index + "]"
            }
        })

        text = text.replace("@Override", "")

        file.text = text
    })
}

def runForFilters(service, closure) {
    service.chains.each {
        def chain = Globals.chainSet.get(it)
        chain.filters.each(closure)
    }
}

def migrateService(service, text, file, fileName) {

    text = text.replaceAll("public[ \t\n]*([A-Za-z]*)[ \t\n]*run", { stuff ->
        "protected " + stuff[1] + " run"
    } )

    text = text.substring(0, text.lastIndexOf('}'))

    def generateInstance = !(text.contains("private static final ") && text.contains(" instance = new "));

    text = text + generateRunWithACL(service, fileName, generateInstance) + "\n}"

    def splitPoint = text.indexOf("import")

    text = text.substring(0, splitPoint) + "import pt.ist.fenixWebFramework.services.Service;\n" + text.substring(splitPoint)

    file.text = text
}

def generateRunWithACL(service, fileName, generateInstance) {

    def nameSplit = service.name.split("\\.")

    def method = ""

    if(generateInstance) {
        method <<= "    private static final " + fileName + " instance = new " + fileName + "();\n\n"
        runForFilters(service, {

            def impl = Globals.filterMap.get(it)
            if(impl == null) {
                println "Impl is null!"
                return
            }

            def filter = impl.split("\\.")
            if(filter.length > 0) {
                method <<= "    private static final " + filter[filter.length - 1] + " " + it[0].toLowerCase() + it[1..-1] + " = new " + filter[filter.length - 1] + "();\n"
            } else {
                println "Len is " + filter.length + " " + impl
            }
        })

        method <<= "\n"
    }

    method <<= "    @Service\n"
    method <<= "    public static " + service.returnType + " run" + nameSplit[nameSplit.length - 1]+ "(" + service.argumentsList() + ") "
    if(service.exceptions != null && !service.exceptions.trim().isEmpty()) {
        method <<= "throws " + service.exceptions + " "
    }
    method <<= "{\n"

    runForFilters(service, {
        method <<= "        " + it[0].toLowerCase() + it[1..-1] + ".execute(" + service.argumentNames() + ");\n"
    })

    method <<= "        "


    if(!"void".equals(service.returnType)) {
        method <<= "return ";
    }
    method <<= "instance.run(" + service.argumentNames() + ");\n"
    method <<= "    }\n"
}
