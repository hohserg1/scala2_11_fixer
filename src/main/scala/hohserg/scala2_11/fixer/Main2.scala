package hohserg.scala2_11.fixer

import org.objectweb.asm.{ClassReader, ClassVisitor, ClassWriter, MethodVisitor, Opcodes}

object Main2 extends App {

  processJars((in, outZipWrapper) => {
    val classReader = new ClassReader(in)
    val classWriter = new ClassWriter(0)
    var shouldSavePatched = false
    classReader.accept(new ClassVisitor(Opcodes.ASM5, classWriter) {
      override def visitMethod(access: Int, name: String, descriptor: String, signature: String, exceptions: Array[String]): MethodVisitor = {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        new MethodVisitor(Opcodes.ASM5, mv) {
          override def visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean): Unit = {
            val itf =
              if (ow2asmKnownInterfaceMethods.get(owner).exists(_.get(name).exists(_.contains(descriptor)))) {
                if (!isInterface)
                  shouldSavePatched = true
                true
              } else
                isInterface
            super.visitMethodInsn(opcode, owner, name, descriptor, itf)
          }
        }
      }
    }, 0)
    outZipWrapper.write(classWriter.toByteArray)
    shouldSavePatched
  })

}
