def source = new File("src/main/java")

def classes = new HashSet()

source.eachFileRecurse { file ->
    if(!file.name.endsWith(".java")) return

        file.text.split("\n").each { line ->
            if(line.matches(".*extends[ \t\n]InfoObject.*")) classes.add(file.path)
        }
}

classes.each { item -> println item }

