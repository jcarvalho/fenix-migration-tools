
def rdo = new File("src/main/java/net/sourceforge/fenixedu/domain/RootDomainObject.java")

def text = rdo.text

def readers = new HashMap()

text.replaceAll("(net\\.sourceforge\\.fenixedu\\.domain\\.[A-Za-z\\.]+)\\ (read[A-Za-z]+ByOID)", {Object[] captured ->
    readers.put(captured[2], captured[1])
    captured[0]
})

readers.each { reader, clazz ->
    def method = ''
    method <<= "    @Deprecated\n"
    method <<= "    public static " + clazz + " " + reader + "(String externalId) {\n"
    method <<= "        " + clazz + " domainObject = FenixFramework.getDomainObject(externalId);\n"
    method <<= "        return (domainObject == null || domainObject.getRootDomainObject() == null) ? null : domainObject;\n"
    method <<= "    }\n\n"
    text = text.concat(method.toString())
}

rdo.text = text