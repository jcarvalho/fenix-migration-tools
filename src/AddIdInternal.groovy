def source = new File("src/main/java")

def target = "target/generated-sources/dml-maven-plugin/"

def classes = new HashSet()

source.eachFileRecurse { file ->
    if(!file.name.endsWith(".java")) return

        file.text.split("\n").each { line ->
            if(line.matches(".*extends[ \t\n][A-Za-z]+_Base.*")) classes.add(file.path)
        }
}

classes.each { item ->
    def sourceFile = new File(item)

    println "Adding getter to class: " + sourceFile

    def sourceLines = sourceFile.text.split("\n")

    def index = sourceLines.length

    while(index > 0) {
        index--
        if(sourceLines[index].contains("}")) {
            break;
        }
    }

    def output = new StringBuffer()

    for(i = 0; i < sourceLines.length; i++) {
        output.append(sourceLines[i] +"\n")
        if(i == (index - 1)) {
            addStuff(output)
        }
    }

    sourceFile.text = output.toString()
}

void addStuff(output) {
    output.append("    @Deprecated\n")
    output.append("    public String getIdInternal() {\n")
    output.append("        return this.getExternalId();\n")
    output.append("    }\n\n")
}