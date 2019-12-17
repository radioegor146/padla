package ru.progrm_jarvis.javacommons.bytecode.asm;

import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import ru.progrm_jarvis.javacommons.classload.ClassFactory;
import ru.progrm_jarvis.javacommons.util.ClassNamingStrategy;

import java.lang.reflect.InvocationTargetException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

class AsmUtilTest {

    private static ClassNamingStrategy classNamingStrategy;

    @BeforeAll
    static void setUp() {
        classNamingStrategy = ClassNamingStrategy.createPaginated(AsmUtilTest.class.getName() + "$$generated$$");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddEmptyConstructor()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        ClassWriter clazz;
        String name, internalName, superClass;
        {
            clazz = new ClassWriter(0);

            name = classNamingStrategy.get();
            internalName = name.replace('.', '/');
            superClass = Type.getInternalName(TestSubject.class);
            clazz.visit(Opcodes.V1_8, AsmUtil.OPCODES_ACC_PUBLIC_FINAL_SUPER, internalName, null, superClass, null);
            AsmUtil.addEmptyConstructor(clazz, superClass);
            clazz.visitEnd();

            val constructor = ((Class<? extends TestSubject>) ClassFactory
                    .defineGCClass(name, clazz.toByteArray())).getDeclaredConstructor();

            assertThat(constructor.getParameterCount(), is(0));

            constructor.setAccessible(true);

            assertThat(constructor.newInstance(), isA(TestSubject.class));
        }
        {
            clazz = new ClassWriter(0);

            name = classNamingStrategy.get();
            internalName = name.replace('.', '/');
            superClass = Type.getInternalName(StatusSubject.class);
            clazz.visit(Opcodes.V1_8, AsmUtil.OPCODES_ACC_PUBLIC_FINAL_SUPER, internalName, null, superClass, null);
            AsmUtil.addEmptyConstructor(clazz, superClass);
            clazz.visitEnd();

            val constructor = ((Class<? extends StatusSubject>) ClassFactory
                    .defineGCClass(name, clazz.toByteArray())).getDeclaredConstructor();

            assertThat(constructor.getParameterCount(), is(0));

            constructor.setAccessible(true);
            try {
                constructor.newInstance();
                // a SuccessStatus should be thrown
                fail();
            } catch (final InvocationTargetException e) {
                assertThat(e.getCause(), isA((Class /* Hamcrest is ill */) SuccessStatus.class));
            }
        }
    }

    @Test
    void testAddEmptyConstructorDefault()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        val clazz = new ClassWriter(0);

        val name = classNamingStrategy.get();
        val internalName = name.replace('.', '/');
        clazz.visit(
                Opcodes.V1_8, AsmUtil.OPCODES_ACC_PUBLIC_FINAL_SUPER, internalName,
                null, AsmUtil.OBJECT_INTERNAL_NAME, null
        );
        AsmUtil.addEmptyConstructor(clazz);
        clazz.visitEnd();

        val constructor = ClassFactory.defineGCClass(name, clazz.toByteArray()).getDeclaredConstructor();

        assertThat(constructor.getParameterCount(), is(0));

        constructor.setAccessible(true);

        val instance = constructor.newInstance();
        assertThat(instance, isA(Object.class));

        assertDoesNotThrow(() -> constructor.newInstance().getClass());
    }

    public static class TestSubject {

        public TestSubject() {}
    }

    public static class StatusSubject {

        public StatusSubject() throws SuccessStatus {
            throw new SuccessStatus();
        }
    }

    private static class SuccessStatus extends Exception {
        public SuccessStatus() {
            super(null, null, true, false);
        }
    }
}