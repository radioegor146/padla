package ru.progrm_jarvis.ultimatemessenger.format.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import ru.progrm_jarvis.javacommons.annotation.Internal;
import ru.progrm_jarvis.javacommons.bytecode.BytecodeLibrary;
import ru.progrm_jarvis.javacommons.bytecode.annotation.UsesBytecodeModification;
import ru.progrm_jarvis.javacommons.bytecode.asm.AsmUtil;
import ru.progrm_jarvis.javacommons.classload.ClassFactory;
import ru.progrm_jarvis.javacommons.lazy.Lazy;
import ru.progrm_jarvis.javacommons.util.ClassNamingStrategy;
import ru.progrm_jarvis.javacommons.util.valuestorage.SimpleValueStorage;
import ru.progrm_jarvis.javacommons.util.valuestorage.ValueStorage;

import java.lang.reflect.InvocationTargetException;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static ru.progrm_jarvis.javacommons.bytecode.asm.AsmUtil.*;

/**
 * Implementation of {@link TextModelFactory text model factory} which uses runtime class generation.
 */
@UsesBytecodeModification(BytecodeLibrary.ASM)
public class AsmTextModelFactory<T> implements TextModelFactory<T> {
    /**
     * Lazy singleton of this text model factory
     */
    private static final Lazy<AsmTextModelFactory> INSTANCE = Lazy.createThreadSafe(AsmTextModelFactory::new);

    /**
     * Returns this {@link TextModelFactory text model factory} singleton.
     *
     * @param <T> generic type of got {@link TextModelFactory text model factory}
     * @return shared instance of this {@link TextModelFactory text model factory}
     */
    @SuppressWarnings("unchecked")
    public static <T> AsmTextModelFactory<T> get() {
        return INSTANCE.get();
    }

    @Override
    @NotNull
    public TextModelFactory.TextModelBuilder<T> newBuilder() {
        return new TextModelBuilder<>();
    }

    /**
     * Implementation of
     * {@link TextModelFactory.TextModelBuilder text model builder}
     * which uses runtime class generation
     * and is capable of joining nearby static text blocks and optimizing {@link #createAndRelease()}.
     *
     * @param <T> type of object according to which the created text models are formatted
     */
    @ToString
    @EqualsAndHashCode(callSuper = true) // simply, why not? :) (this will also allow caching of instances)
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    protected static class TextModelBuilder<T> extends AbstractGeneratingTextModelFactoryBuilder<T> {

        /**
         * Internal storage of {@link TextModel dynamic text models} passed to {@code static final} fields.
         */
        protected static final ValueStorage<String, TextModel<?>> DYNAMIC_MODELS = new SimpleValueStorage<>();

        /**
         * Class naming strategy used to allocate names for generated classes
         */
        @NonNull private static final ClassNamingStrategy CLASS_NAMING_STRATEGY = ClassNamingStrategy.createPaginated(
                TextModelBuilder.class.getName() + "$$Generated$$TextModel$$"
        );

        //<editor-fold desc="Bytecode generation constants" defaultstate="collapsed">

        ///////////////////////////////////////////////////////////////////////////
        // Types
        ///////////////////////////////////////////////////////////////////////////
        /* ******************************************** ASM Type objects ******************************************** */
        /**
         * ASM type of {@link TextModelBuilder}
         */
        protected static final Type TEXT_MODEL_BUILDER_TYPE = getType(TextModelBuilder.class),
        /**
         * ASM type of {@link StringBuilder}
         */
        STRING_BUILDER_TYPE = getType(StringBuilder.class),
        /**
         * ASM type of {@link TextModel}
         */
        TEXT_MODEL_TYPE = getType(TextModel.class);
        ///////////////////////////////////////////////////////////////////////////
        // Strings
        ///////////////////////////////////////////////////////////////////////////
        /* ******************************************* Parts of this API ******************************************* */
        /**
         * Prefix of generated fields after which the index will go
         */
        protected static final String GENERATED_FIELD_NAME_PREFIX = "D",
        /**
         * Name of parent generic in current context
         */
        PARENT_T_GENERIC_DESCRIPTOR = "TT;",
        /* ********************************************** Method names ********************************************** */
        /**
         * Name of {@link TextModel#getText(Object)} method
         */
        GET_TEXT_METHOD_NAME = "getText",
        /**
         * Name of {@link StringBuilder}{@code .append(}<i>?</i>i{@code )} method
         */
        APPEND_METHOD_NAME = "append",
        /**
         * Name of {@link TextModelBuilder#internal$getDynamicTextModel(String)} method
         */
        INTERNAL_GET_DYNAMIC_TEXT_MODEL_METHOD_NAME = "internal$getDynamicTextModel",
        /* ********************************************* Internal names ********************************************* */
        /**
         * Internal name of {@link TextModel}
         */
        TEXT_MODEL_BUILDER_INTERNAL_NAME = TEXT_MODEL_BUILDER_TYPE.getInternalName(),
        /**
         * Internal name of {@link StringBuilder}
         */
        STRING_BUILDER_INTERNAL_NAME = STRING_BUILDER_TYPE.getInternalName(),
        /**
         * Internal name of {@link TextModel}
         */
        TEXT_MODEL_INTERNAL_NAME = TEXT_MODEL_TYPE.getInternalName(),
        /* ********************************************** Descriptors ********************************************** */
        /**
         * Descriptor of {@link TextModel}
         */
        TEXT_MODEL_BUILDER_DESCRIPTOR = TEXT_MODEL_BUILDER_TYPE.getDescriptor(),
        /**
         * Descriptor of {@link StringBuilder}
         */
        STRING_BUILDER_DESCRIPTOR = STRING_BUILDER_TYPE.getDescriptor(),
        /**
         * Descriptor of {@link TextModel}
         */
        TEXT_MODEL_DESCRIPTOR = TEXT_MODEL_TYPE.getDescriptor(),
        /* ********************************** Method descriptors (aka signatures) ********************************** */
        /**
         * Signature of {@code TextModel(Object)} method
         */
        STRING_OBJECT_METHOD_DESCRIPTOR = getMethodType(STRING_TYPE, OBJECT_TYPE).getDescriptor(),
        /**
         * Signature of {@code void()} method
         */
        VOID_METHOD_DESCRIPTOR = getMethodType(VOID_TYPE).getDescriptor(),
        /**
         * Signature of {@code void(int)} method
         */
        VOID_INT_METHOD_DESCRIPTOR = getMethodType(VOID_TYPE, INT_TYPE).getDescriptor(),
        /**
         * Signature of {@code void(String)} method
         */
        VOID_STRING_METHOD_DESCRIPTOR = getMethodType(VOID_TYPE, STRING_TYPE).getDescriptor(),
        /**
         * Signature of {@code String()} method
         */
        STRING_METHOD_SIGNATURE = getMethodDescriptor(STRING_TYPE),
        /**
         * Signature of {@code StringBuilder(String)} method
         */
        STRING_BUILDER_STRING_METHOD_SIGNATURE = getMethodDescriptor(STRING_BUILDER_TYPE, STRING_TYPE),
        /**
         * Signature of {@code TextModel(String)} method
         */
        TEXT_MODEL_STRING_METHOD_SIGNATURE = getMethodDescriptor(TEXT_MODEL_TYPE, STRING_TYPE),
        /**
         * Signature of {@code StringBuilder(char)} method
         */
        STRING_BUILDER_CHAR_METHOD_SIGNATURE = getMethodDescriptor(STRING_BUILDER_TYPE, CHAR_TYPE),
        /**
         * Generic signature of {@link TextModel#getText(Object)} method
         */
        STRING_GENERIC_T_METHOD_SIGNATURE = '(' + PARENT_T_GENERIC_DESCRIPTOR + ')' + STRING_DESCRIPTOR,
        /* ******************************************* Generic signatures ******************************************* */
        /**
         * Generic descriptor of {@link TextModel}
         */
        TEXT_MODEL_SIGNATURE = 'L' + TEXT_MODEL_INTERNAL_NAME + '<' + PARENT_T_GENERIC_DESCRIPTOR + ">;",
        /**
         * Generic signature of the generated class
         *
         * @see #PARENT_T_GENERIC_DESCRIPTOR name of the parent generic type
         */
        GENERIC_CLASS_SIGNATURE = "<T:" + OBJECT_DESCRIPTOR + '>' + OBJECT_DESCRIPTOR + TEXT_MODEL_SIGNATURE;

        ///////////////////////////////////////////////////////////////////////////
        // Ints
        ///////////////////////////////////////////////////////////////////////////
        /* *************************************** Precomputed string lengths *************************************** */
        /**
         * Length of {@link #TEXT_MODEL_DESCRIPTOR}
         */
        private final int TEXT_MODEL_DESCRIPTOR_LENGTH = TEXT_MODEL_DESCRIPTOR.length(),
        /**
         * Length of {@link #TEXT_MODEL_SIGNATURE}
         */
        TEXT_MODEL_GENERIC_DESCRIPTOR_LENGTH = TEXT_MODEL_SIGNATURE.length();

        ///////////////////////////////////////////////////////////////////////////
        // Constant String arrays
        ///////////////////////////////////////////////////////////////////////////
        /* ***************************************** Arrays of descriptors ***************************************** */
        /**
         * Array whose only value is {@link #TEXT_MODEL_INTERNAL_NAME}.
         */
        protected static final String[] TEXT_MODEL_INTERNAL_NAME_ARRAY = new String[]{TEXT_MODEL_INTERNAL_NAME};

        //</editor-fold>

        /**
         * Retrieves (gets and removes) {@link TextModel dynamic text model}
         * stored in {@link #DYNAMIC_MODELS} by the given key.
         *
         * @param uniqueKey unique key by which the value should be retrieved
         * @return dynamic text model stored by the given unique key
         * @deprecated this method is internal
         */
        @Deprecated
        @Internal("This is expected to be invoked only by generated TextModels to initialize their fields")
        public static TextModel<?> internal$getDynamicTextModel(@NotNull final String uniqueKey) {
            return DYNAMIC_MODELS.retrieveValue(uniqueKey);
        }

        @Override
        @NotNull
        protected TextModel<T> performTextModelCreation(final boolean release) {
            val clazz = new ClassWriter(0); // MAXs are already computed 😎

            //<editor-fold desc="ASM class generation" defaultstate="collapsed">
            val className = CLASS_NAMING_STRATEGY.get();

            val dynamicElements = dynamicElementCount; // at least 1

            // ASM does not provide any comfortable method fot this :(
            // PS yet ASM is <3
            val internalClassName = className.replace('.', '/');
            clazz.visit(
                    V1_8 /* generate bytecode for JVM1.8 */, OPCODES_ACC_PUBLIC_FINAL_SUPER,
                    internalClassName, GENERIC_CLASS_SIGNATURE, OBJECT_INTERNAL_NAME /* inherit Object */,
                    TEXT_MODEL_INTERNAL_NAME_ARRAY /* implement TextModel interface */
            );
            // add an empty constructor
            AsmUtil.addEmptyConstructor(clazz);

            { // Implement `TextModel#getText(T)` method and add fields
                val method = clazz.visitMethod(
                        ACC_PUBLIC, GET_TEXT_METHOD_NAME, STRING_OBJECT_METHOD_DESCRIPTOR,
                        STRING_GENERIC_T_METHOD_SIGNATURE, null
                );

                method.visitCode();

                //<editor-fold desc="Method code generation" defaultstate="collapsed">
                {
                    val staticInitializer = AsmUtil.visitStaticInitializer(clazz);
                    staticInitializer.visitCode();
                    val staticLength = this.staticLength;
                    if (staticLength == 0) { // there are no static elements (and at least 2 dynamic)
                        /* ************************ Invoke `StringBuilder(int)` constructor ************************ */
                        method.visitTypeInsn(NEW, STRING_BUILDER_INTERNAL_NAME);
                        method.visitInsn(DUP);
                        String fieldName = GENERATED_FIELD_NAME_PREFIX + 0;
                        // Specify first `StringBuilder` element
                        asm$pushStaticTextModelFieldGetTextInvocationResult(method, internalClassName, fieldName);
                        // Call constructor `StringBuilder(int)`
                        method.visitMethodInsn(
                                INVOKESPECIAL, STRING_BUILDER_INTERNAL_NAME,
                                CONSTRUCTOR_METHOD_NAME, VOID_STRING_METHOD_DESCRIPTOR, false
                        );

                        val iterator = elements.iterator();
                        asm$addStaticFieldWithInitializer(
                                clazz, internalClassName, staticInitializer,
                                fieldName, iterator.next().getDynamicContent()
                        );
                        // dynamic elements count is at least 2
                        var dynamicIndex = 0;
                        while (iterator.hasNext()) {
                            fieldName = (GENERATED_FIELD_NAME_PREFIX + (++dynamicIndex));

                            asm$addStaticFieldWithInitializer(
                                    clazz, internalClassName, staticInitializer,
                                    fieldName, iterator.next().getDynamicContent()
                            );
                            asm$pushStaticTextModelFieldGetTextInvocationResult(method, internalClassName, fieldName);
                            asm$invokeStringBuilderAppendString(method);
                        }

                        method.visitMaxs(4, 2 /* [StringBuilder instance ] + [this | appended value]*/);
                    } else { // there are static elements
                        /* ************************ Invoke `StringBuilder(int)` constructor ************************ */
                        method.visitTypeInsn(NEW, STRING_BUILDER_INTERNAL_NAME);
                        method.visitInsn(DUP);
                        // Specify initial length of StringBuilder via its constructor
                        pushInt(method, staticLength);
                        // Call constructor `StringBuilder(int)`
                        method.visitMethodInsn(
                                INVOKESPECIAL, STRING_BUILDER_INTERNAL_NAME,
                                CONSTRUCTOR_METHOD_NAME, VOID_INT_METHOD_DESCRIPTOR, false
                        );
                        /* ********************************** Append all elements ********************************** */
                        int dynamicIndex = -1;
                        // Lists are commonly faster with random access
                        for (val element : elements) {
                            // Load static text value from dynamic constant
                            if (element.isDynamic()) {
                                val fieldName = GENERATED_FIELD_NAME_PREFIX + (++dynamicIndex);
                                asm$addStaticFieldWithInitializer(
                                        clazz, internalClassName, staticInitializer,
                                        fieldName, element.getDynamicContent()
                                );
                                asm$pushStaticTextModelFieldGetTextInvocationResult(
                                        method, internalClassName, fieldName
                                );
                                asm$invokeStringBuilderAppendString(method);
                            } else {
                                val staticContent = element.getStaticContent();
                                if (staticContent.length() == 1) {
                                    pushCharUnsafely(method, staticContent.charAt(0));
                                    asm$invokeStringBuilderAppendChar(method);
                                } else {
                                    method.visitLdcInsn(element.getStaticContent()); // get constant String value
                                    asm$invokeStringBuilderAppendString(method);
                                }
                            }
                        }
                        method.visitMaxs(3, 2 /* [StringBuilder instance] + [this|appended value] */);
                    }
                    staticInitializer.visitInsn(RETURN);
                    staticInitializer.visitMaxs(2, 0);
                    staticInitializer.visitEnd();
                }

                // invoke `StringBuilder#toString()`
                method.visitMethodInsn(
                        INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_NAME,
                        TO_STRING_METHOD_NAME, STRING_METHOD_SIGNATURE, false
                );
                // Return String from method
                method.visitInsn(ARETURN);
                //</editor-fold>

                // Note: visitMaxs() happens above
                method.visitEnd();
            }

            clazz.visitEnd();
            //</editor-fold>

            try {
                val constructor = ClassFactory.defineGCClass(className, clazz.toByteArray()).getDeclaredConstructor();
                constructor.setAccessible(true);
                //noinspection unchecked
                return (TextModel<T>) constructor.newInstance();
            } catch (final NoSuchMethodException | InstantiationException
                    | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not compile and instantiate TextModel from the given elements");
            }
        }

        /**
         * Adds code to the method so that it invokes {@link TextModel#getText(Object)}
         * taking object for it from the field.
         *
         * @param method method visitor through which the code should be updated
         * @param internalClassName internal name of this class
         * @param fieldName name of the field of type {@link TextModel}
         */
        protected static void asm$pushStaticTextModelFieldGetTextInvocationResult(
                @NotNull final MethodVisitor method,
                @NotNull final String internalClassName,
                @NotNull final String fieldName) {
            // Get value of field storing dynamic value
            method.visitFieldInsn(GETSTATIC, internalClassName, fieldName, TEXT_MODEL_DESCRIPTOR);
            // Push target
            method.visitVarInsn(ALOAD, 1);
            // Invoke `TextModel.getText(T)` on field's value
            method.visitMethodInsn(
                    INVOKEINTERFACE, TEXT_MODEL_INTERNAL_NAME, GET_TEXT_METHOD_NAME,
                    STRING_OBJECT_METHOD_DESCRIPTOR, true
            );
        }

        /**
         * Adds code to the method so that it invokes {@link StringBuilder#append(String)}.
         *
         * @param method method visitor through which the code should be updated
         */
        protected static void asm$invokeStringBuilderAppendString(@NotNull final MethodVisitor method) {
            // Invoke `StringBuilder.append(String)`
            method.visitMethodInsn(
                    INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_NAME,
                    APPEND_METHOD_NAME, STRING_BUILDER_STRING_METHOD_SIGNATURE, false
            );
        }

        /**
         * Adds code to the method so that it invokes {@link StringBuilder#append(String)}.
         *
         * @param method method visitor through which the code should be updated
         */
        protected static void asm$invokeStringBuilderAppendChar(@NotNull final MethodVisitor method) {
            // Invoke `StringBuilder.append(char)`
            method.visitMethodInsn(
                    INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_NAME,
                    APPEND_METHOD_NAME, STRING_BUILDER_CHAR_METHOD_SIGNATURE, false
            );
        }

        /**
         * Adds a {@code static final} field of type {@link TextModel} initialized via static-initializer block
         * invoking {@link #internal$getDynamicTextModel(String)} to the class.
         *
         * @param clazz class to which the field should be added
         * @param internalClassName internal name of this class
         * @param staticInitializer static initializer block
         * @param fieldName name of the field to store value
         * @param value value of the field (dynamic text model)
         */
        protected static void asm$addStaticFieldWithInitializer(@NotNull final ClassVisitor clazz,
                                                                @NotNull final String internalClassName,
                                                                @NotNull final MethodVisitor staticInitializer,
                                                                @NotNull final String fieldName,
                                                                @NotNull final TextModel value) {
            // add field
            clazz.visitField(
                    OPCODES_ACC_PUBLIC_STATIC_FINAL /* less access checks & possible JIT folding */,
                    fieldName, TEXT_MODEL_DESCRIPTOR /* field type is TextModel<T> */,
                    TEXT_MODEL_SIGNATURE, null /* no default value [*] */
            ).visitEnd();

            // push unique key
            staticInitializer.visitLdcInsn(DYNAMIC_MODELS.storeValue(value));
            // invoke `TextModel internal$getDynamicTextModel(String)`
            staticInitializer.visitMethodInsn(
                    INVOKESTATIC, TEXT_MODEL_BUILDER_INTERNAL_NAME,
                    INTERNAL_GET_DYNAMIC_TEXT_MODEL_METHOD_NAME, TEXT_MODEL_STRING_METHOD_SIGNATURE, false
            );

            // set the field to the computed value
            staticInitializer.visitFieldInsn(PUTSTATIC, internalClassName, fieldName, TEXT_MODEL_DESCRIPTOR);
        }
    }
}