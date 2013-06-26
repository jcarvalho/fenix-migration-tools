
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


        first = true
        service.returnType = stuff[1]
        def arguments =  stuff[2].replace('\n', ' ').replace('\t', ' ').replace("final", "")

        service.exceptions = stuff[4]

        arguments.split(",").each { argument ->
            if(argument.trim().length() > 0)
                service.arguments.add(argument.trim())
        }
    })

    // migrateFilters(service)

    migrateService(service, text, file, fileName)
}
/*
 Globals.filterMap.each { name, impl ->
 def file = new File("src/main/java/" + impl.replace('.', '/') + ".java")
 def fileParts = impl.split("\\.")
 def fileName = fileParts[fileParts.length - 1]
 def text = file.text
 if(text.contains(fileName + " instance"))
 return;
 def splitIndex = text.indexOf('{');
 def contents = text[0..splitIndex]
 contents <<= "\n\n    public static final "
 contents <<= fileName
 contents <<= " instance = new "
 contents <<= fileName
 contents <<= "();"
 contents <<= text[splitIndex+1..-1]
 file.text = contents.toString()
 }
 */
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

    def generateInstance = !(text.contains("private static final ") && text.contains(" serviceInstance = new "));

    text = text + "    // Service Invokers migrated from Berserk\n\n"

    text = text + generateRunWithACL(service, fileName, generateInstance)


    text = text + "\n}"


    def splitPoint = text.indexOf("import")

    text = text.substring(0, splitPoint) +
            "import pt.ist.fenixframework.Atomic;\nimport net.sourceforge.fenixedu.applicationTier.Servico.exceptions.NotAuthorizedException;\n"   +
            text.substring(splitPoint)

    // text = text.replace(" extends FenixService", "")

    file.text = text
}

def simplify(className) {
    def nameSplit = className.split("\\.")
    nameSplit[nameSplit.length - 1]
}

def countFilters(service) {
    def counter = 0
    runForFilters(service, { counter++ })
    counter
}

def generateRunWithACL(service, fileName, generateInstance) {

    def nameSplit = service.name.split("\\.")

    def method = ""

    def filterCount = countFilters(service)

    if(generateInstance) {
        method <<= "    private static final " + fileName + " serviceInstance = new " + fileName + "();\n\n"
    }

    method <<= "    @Atomic\n"
    method <<= "    public static " + service.returnType + " run" + nameSplit[nameSplit.length - 1]+ "(" + service.argumentsList() + ") "
    if(service.exceptions != null && !service.exceptions.trim().isEmpty()) {
        method <<= "throws " + service.exceptions + " "
        if(filterCount > 0) {
            method <<= ", NotAuthorizedException "
        }
    } else if(filterCount > 0 ) {
        method <<= "throws NotAuthorizedException "
    }
    method <<= "{\n"

    def count = 1

    runForFilters(service, {
        def impl = Globals.filterMap.get(it)
        if(impl == null) {
            println "Null impl for " + it + " on service " + service.name
        } else {
            if(filterCount > 1) {
                method <<= "        try {\n"
                method <<= "            " + simplify(Globals.filterMap.get(it)) + ".instance.execute(" + service.argumentNames() + ");\n"
                method <<= "            " + invocation(service)
                method <<= "        } catch (NotAuthorizedException ex" + count++ + ") {\n"
            } else {
                method <<= "        " + simplify(Globals.filterMap.get(it)) + ".instance.execute(" + service.argumentNames() + ");\n"
            }
        }
    })

    if(filterCount <= 1) {
        method <<= "        "
        method <<= invocation(service)
    } else {
        method <<= "        throw ex" + (count - 1) + ";\n"
        method <<= "        "
        while(count > 1) {
            method <<= "        }\n"
            count--
        }
    }
    method <<= "    }\n"
}

def invocation(service) {
    def invoc = "";
    if(!"void".equals(service.returnType)) {
        invoc <<= "return ";
    }
    invoc <<= "serviceInstance.run(" + service.argumentNames() + ");\n"
    invoc
}
