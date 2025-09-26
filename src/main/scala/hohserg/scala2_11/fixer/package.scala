package hohserg.scala2_11

import java.io.{DataInputStream, DataOutputStream, File, FileOutputStream, FilenameFilter}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter


package object fixer {
  val knownInterfaceMethods = Set(
    "gloomyfolken/hooklib/api/ReturnSolve.no:()Lgloomyfolken/hooklib/api/ReturnSolve;"
    )
  val ow2asmKnownInterfaceMethods = knownInterfaceMethods.map { e =>
      val Array(owner, method) = e.split('.')
      val Array(methodName, methodDesc) = method.split(':')
      owner -> (methodName, methodDesc)
    }.groupBy { case (owner, _) => owner }
    .map { case (owner, methods) =>
      owner ->
        methods.groupBy { case (_, (methodName, _)) => methodName }
          .map { case (methodName, descriptions) =>
            methodName ->
              descriptions.map { case (_, (_, methodDesc)) => methodDesc }
          }
    }

  def processJars(f: (DataInputStream, DataOutputStream) => Boolean): Unit = {
    val workingDir = new File(".")
    val resultDir = new File("patched")
    resultDir.mkdir()
    val jarFiles = workingDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
    })

    for (modFile <- jarFiles) {
      val inZip = new ZipFile(modFile)
      val outZipLocation = new File(resultDir, modFile.getName)
      val outZip = new ZipOutputStream(new FileOutputStream(outZipLocation))
      var shouldSavePatched = false
      val outZipWrapper = new DataOutputStream(outZip)
      for (inFile <- inZip.entries().asScala) {
        val in = new DataInputStream(inZip.getInputStream(inFile))
        val outFile = new ZipEntry(inFile)
        outZip.putNextEntry(outFile)
        if (inFile.isDirectory)
          outZip.closeEntry()
        else {
          if (inFile.getName.endsWith(".class")) {
            val changed = f(in, outZipWrapper)
            shouldSavePatched = shouldSavePatched || changed
          } else {
            in.transferTo(outZip)
          }
        }
        in.close()
      }
      outZip.close()
      if (!shouldSavePatched) {
        outZipLocation.delete()
      }
    }
  }
}
