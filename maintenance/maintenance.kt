import com.google.common.io.BaseEncoding
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.BufferedInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

fun main(args: Array<String>) { // run with WORKSPACE file path as the first arg
    val out = Paths.get("${args[0]}.out")
    Files.copy(Paths.get(args[0]), out, StandardCopyOption.REPLACE_EXISTING)
    bumpEap("232", out)
    bumpPlugins("232", out)
    bumpRelease("2023.1", "231", out)
    bumpPlugins("231", out)
    bumpMavenPackages("junit:junit", "JUNIT", out)
}

fun bumpMavenPackages(coordinates: String, variablePrefix: String, out: Path) {
    val latestVersion = latestVersion(coordinates)
    val packageName = coordinates.split( ":").last()
    val jarUrl = "https://repo1.maven.org/maven2/${coordinates.replace(":", "/").replace(".", "/")}/$latestVersion/$packageName-$latestVersion.jar"
    val sha = shaOfUrl(jarUrl)
    val content = Files.readString(out)
            .insertWorkspaceValue("${variablePrefix}_ARTIFACT", "$coordinates:$latestVersion")
            .insertWorkspaceValue("${variablePrefix}_SHA", sha)
    Files.writeString(out, content)
}

private fun latestVersion(coordinates: String): String {
    val metadataAddress = "https://repo1.maven.org/maven2/${coordinates.replace(".", "/").replace(":", "/")}/maven-metadata.xml"
    val metadata = URL(metadataAddress).readText()
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val plugins = builder.parse(metadata.byteInputStream()).documentElement.getElementsByTagName("versioning")
    return plugins.item(0).childNodes.toList().first { it.nodeName == "latest" }.firstChild.nodeValue
}

fun bumpRelease(version: String, major: String, out: Path) {
    val releasesPage = URL("https://www.jetbrains.com/intellij-repository/releases").readText();
    val clionRelease = latestRelease(version, releasesPage, "clion")
    val icRelease = latestRelease(version, releasesPage, "ideaIC")
    val iuRelease = latestRelease(version, releasesPage, "ideaIU")

    bump(
        workspaceShaVarName = "IC_${major}_SHA",
        workspaceUrlVarName = "IC_${major}_URL",
        downloadUrl = icRelease,
        workspace = out,
    )
    bump(
        workspaceShaVarName = "IU_${major}_SHA",
        workspaceUrlVarName = "IU_${major}_URL",
        downloadUrl = iuRelease,
        workspace = out,
    )
    bump(
        workspaceShaVarName = "CLION_${major}_SHA",
        workspaceUrlVarName = "CLION_${major}_URL",
        downloadUrl = clionRelease,
        workspace = out,
    )
    println("$clionRelease, $icRelease, $iuRelease")
}

private fun bumpEap(intellijMajorVersion: String, out: Path) {
    val ijLatestVersion = getLatestVersion("idea", intellijMajorVersion)
    val clionLatestVersion = getLatestVersion("clion", intellijMajorVersion)

    println(ijLatestVersion)
    println(clionLatestVersion)
    bump(
        workspaceShaVarName = "IC_${intellijMajorVersion}_SHA",
        workspaceUrlVarName = "IC_${intellijMajorVersion}_URL",
        downloadUrl = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/${ijLatestVersion}-EAP-SNAPSHOT/ideaIC-${ijLatestVersion}-EAP-SNAPSHOT.zip",
        workspace = out,
    )
    bump(
        workspaceShaVarName = "IU_${intellijMajorVersion}_SHA",
        workspaceUrlVarName = "IU_${intellijMajorVersion}_URL",
        downloadUrl = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIU/${ijLatestVersion}-EAP-SNAPSHOT/ideaIU-${ijLatestVersion}-EAP-SNAPSHOT.zip",
        workspace = out,
    )
    bump(
        workspaceShaVarName = "CLION_${intellijMajorVersion}_SHA",
        workspaceUrlVarName = "CLION_${intellijMajorVersion}_URL",
        downloadUrl = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/clion/clion/${clionLatestVersion}-EAP-SNAPSHOT/clion-${clionLatestVersion}-EAP-SNAPSHOT.zip",
        workspace = out,
    )
    println(out.toAbsolutePath())
}

private fun bumpPlugins(intellijMajorVersion: String, out: Path) {
    val goPluginLatestVersion = pluginLatestVersion("org.jetbrains.plugins.go", intellijMajorVersion)
    val scalaPluginVersion = pluginLatestVersion("org.intellij.scala", intellijMajorVersion)
    val pythonPluginVersion = pluginLatestVersion("PythonCore", intellijMajorVersion)
    bump(
        workspaceShaVarName = "PYTHON_PLUGIN_${intellijMajorVersion}_SHA",
        workspaceUrlVarName = "PYTHON_PLUGIN_${intellijMajorVersion}_URL",
        downloadUrl = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/${pythonPluginVersion}/PythonCore-${pythonPluginVersion}.zip",
        workspace = out
    )

    bump(
        workspaceShaVarName = "GO_PLUGIN_${intellijMajorVersion}_SHA",
        workspaceUrlVarName = "GO_PLUGIN_${intellijMajorVersion}_URL",
        downloadUrl = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/${goPluginLatestVersion}/org.jetbrains.plugins.go-${goPluginLatestVersion}.zip",
        workspace = out
    )
    bump(
        workspaceShaVarName = "SCALA_PLUGIN_${intellijMajorVersion}_SHA",
        workspaceUrlVarName = "SCALA_PLUGIN_${intellijMajorVersion}_URL",
        downloadUrl = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/${scalaPluginVersion}/org.intellij.scala-${scalaPluginVersion}.zip",
        workspace = out
    )
}

private fun bump(downloadUrl: String, workspace: Path?, workspaceShaVarName: String, workspaceUrlVarName: String) {
    val regex = "$workspaceUrlVarName = \"(.*)\"".toRegex()
    val currentURL = regex.find(Files.readString(workspace))!!.destructured.toList()[0]
    if(currentURL == downloadUrl) {
        println("${Paths.get(currentURL).fileName} is up to date");
        return;
    }
    val icSha = shaOfUrl(downloadUrl)
    val content = Files.readString(workspace)
        .insertWorkspaceValue(workspaceShaVarName, icSha)
        .insertWorkspaceValue(workspaceUrlVarName, downloadUrl)
    Files.writeString(workspace, content)
}

private fun shaOfUrl(icUrl: String): String {
    val icStream = BufferedInputStream(URL(icUrl).openStream())
    val digest = MessageDigest.getInstance("SHA-256")
    icStream.iterator().withIndex().forEachRemaining {
        digest.update(it.value)
        if (it.index % 10000000 == 0) {
            println("${it.index / 1024 / 1024} mb of ${URL(icUrl).file.split("/").last()} processed")
        }
    }
    val sha256sum = digest.digest(icStream.readAllBytes())
    return BaseEncoding.base16().encode(sha256sum).lowercase()
}

private fun getLatestVersion(product: String, major: String): String {
    val ijVersionUrl = URL("https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/$product/BUILD/$major-EAP-SNAPSHOT/BUILD-$major-EAP-SNAPSHOT.txt")
    return BufferedInputStream(ijVersionUrl.openStream()).reader().readText()
}

private fun latestRelease(version: String, releasesPage: String, product: String): String {
    val productFamily = when(product) {
        "ideaIC" -> "idea"
        "ideaIU" -> "idea"
        "clion" -> "clion"
        else -> throw RuntimeException("No such product: $product")
    }
    return "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/$productFamily/$product/$version.([\\d+])/$product-$version.[\\d+].zip".toRegex()
        .findAll(releasesPage).maxBy { it.groupValues[1].toInt() }.value
}

fun pluginLatestVersion(pluginId: String, major: String): String? {
    val pluginListUrl = URL("https://plugins.jetbrains.com/plugins/list?pluginId=$pluginId").readText()
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val plugins: NodeList = builder.parse(pluginListUrl.byteInputStream()).documentElement.childNodes.item(1).childNodes
    val compatiblePlugin =  plugins.toList().firstOrNull {plugin ->
        val ideaVersionNode = plugin.childNodes.toList().first{ it.nodeName == "idea-version" }
        val until = ideaVersionNode.attributes.getNamedItem("until-build")
        until.nodeValue.startsWith("${major}.")
    }
    val pluginVersion = compatiblePlugin?.childNodes?.toList()?.firstOrNull { it.nodeName == "version" }
    return pluginVersion?.childNodes?.toList()?.first()?.nodeValue
}

fun String.insertWorkspaceValue(workspaceShaVarName: String, icSha: String): String =
    this.replace("$workspaceShaVarName =.*".toRegex(), """$workspaceShaVarName = "$icSha"""")

fun NodeList.toList(): List<Node> {
    return (0 until this.length).map { this.item(it) }
}