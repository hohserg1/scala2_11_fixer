package hohserg.scala2_11.fixer

import java.io.{DataInputStream, DataOutputStream, File, FileOutputStream, FilenameFilter, InputStream, OutputStream}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.collection.mutable

//based on https://stackoverflow.com/a/25732410/11485264
//and https://docs.oracle.com/javase/specs/jvms/se6/html/ConstantPool.doc.html
//and https://docs.oracle.com/javase/specs/jvms/se6/html/ClassFile.doc.html#42041
object Main extends App {

  val knownInterfaceMethods = Set(
    "gloomyfolken/hooklib/api/ReturnSolve.no:()Lgloomyfolken/hooklib/api/ReturnSolve;"
    )

  val workingDir = new File(".")
  val resultDir = new File("patched")
  resultDir.mkdir()
  val jarFiles = workingDir.listFiles(new FilenameFilter {
    override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
  })
  val copyBuffer = new Array[Byte](16)

  def copy(in: InputStream, out: OutputStream, size: Int): Unit = {
    in.readNBytes(copyBuffer, 0, size)
    out.write(copyBuffer, 0, size)
  }

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
          outZipWrapper.writeInt(in.readInt())
          copy(in, outZipWrapper, 4)
          val size = in.readUnsignedShort()
          outZipWrapper.writeShort(size)
          val constantPool = new mutable.HashMap[Int, String]()
          var i = 1
          while (i < size) {
            val tag = in.readUnsignedByte()
            Constant.constant(tag) match {
              case Constant.Utf8 =>
                val v = in.readUTF()

                outZipWrapper.writeByte(tag)
                outZipWrapper.writeUTF(v)

                constantPool += i -> v

              case Constant.Long | Constant.Double =>
                outZipWrapper.writeByte(tag)
                copy(in, outZipWrapper, Constant.constant(tag).size)
                i += 1

              case Constant.Method =>
                val classIndex = in.readUnsignedShort()
                val nameAndTypeIndex = in.readUnsignedShort()

                val shouldBeInterfaceMethod = knownInterfaceMethods.contains(constantPool(classIndex) + "." + constantPool(nameAndTypeIndex))
                if (shouldBeInterfaceMethod)
                  shouldSavePatched = true

                outZipWrapper.writeByte(if (shouldBeInterfaceMethod) Constant.InterfaceMethod.tag else tag)
                outZipWrapper.writeShort(classIndex)
                outZipWrapper.writeShort(nameAndTypeIndex)

              case Constant.Class =>
                val nameIndex = in.readUnsignedShort()

                outZipWrapper.writeByte(tag)
                outZipWrapper.writeShort(nameIndex)

                constantPool += i -> constantPool(nameIndex)

              case Constant.NameAndType =>
                val nameIndex = in.readUnsignedShort()
                val descIndex = in.readUnsignedShort()

                outZipWrapper.writeByte(tag)
                outZipWrapper.writeShort(nameIndex)
                outZipWrapper.writeShort(descIndex)

                constantPool += i -> (constantPool(nameIndex) + ":" + constantPool(descIndex))

              case other =>
                outZipWrapper.writeByte(tag)
                copy(in, outZipWrapper, other.size);
            }
            i += 1
          }
          in.transferTo(outZip)

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
