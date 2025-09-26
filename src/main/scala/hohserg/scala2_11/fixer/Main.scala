package hohserg.scala2_11.fixer

import java.io.{InputStream, OutputStream}
import scala.collection.mutable

//based on https://stackoverflow.com/a/25732410/11485264
//and https://docs.oracle.com/javase/specs/jvms/se6/html/ConstantPool.doc.html
//and https://docs.oracle.com/javase/specs/jvms/se6/html/ClassFile.doc.html#42041
object Main extends App {

  val copyBuffer = new Array[Byte](16)

  def copy(in: InputStream, out: OutputStream, size: Int): Unit = {
    in.readNBytes(copyBuffer, 0, size)
    out.write(copyBuffer, 0, size)
  }

  processJars((in, outZipWrapper) => {
    var shouldSavePatched = false
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
    in.transferTo(outZipWrapper)
    shouldSavePatched
  })
}
