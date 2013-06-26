def pages = new LinkedList();

pages.add("http://localhost:8080/fenix/cursos/mec/descricao")

def alreadyChecked = new HashSet()

def roosterFile = new File("roosters")
def warningsFile = new File("warnings")

roosterFile.createNewFile()
warningsFile.createNewFile()

while(!pages.isEmpty()) {

    def url = pages.pop();

    url = url.replace("&amp;", "&")

    if(url.startsWith("/")) {
        url = "http://localhost:8080" + url
    }

    if(alreadyChecked.contains(url) || !url.contains("localhost:8080")) {
        continue;
    }

    println "Checking " + url

    alreadyChecked.add(url)

    try {
        def page = url.toURL().text;


        if(page.contains("- - - - - - - - - - - Error Origin - - - - - - - - - - -")) {
            roosterFile.text = roosterFile.text + "\n" + url
        }

        page.replaceAll("a href=\"(.*)\"", { stuff ->
            def link = stuff[1].split("\"")[0]
            if(link.contains("mailto") || link.contains("www") || link.contains("https://") || link.contains("/dspace/bitstream")) {
                return
            }
            pages.add(link)
        })
    } catch (FileNotFoundException e) {
        // ignore
    } catch (IOException e) {
        if(e.getMessage().contains("500")) {
            roosterFile.text = roosterFile.text + "\n" + url
        } else {
            warningsFile.text = warningsFile.text +  "\nException while handling URL: " + e
        }
    }
    catch (Exception e) {
        warningsFile.text = warningsFile.text +  "\nException while handling URL: " + e
    }
}
