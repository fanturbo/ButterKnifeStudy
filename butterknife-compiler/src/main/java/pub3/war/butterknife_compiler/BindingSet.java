package pub3.war.butterknife_compiler;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.google.auto.common.MoreElements.getPackage;
import static java.util.Collections.singletonList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static pub3.war.butterknife_compiler.ButterKnifeProcessor.ACTIVITY_TYPE;
import static pub3.war.butterknife_compiler.ButterKnifeProcessor.DIALOG_TYPE;
import static pub3.war.butterknife_compiler.ButterKnifeProcessor.VIEW_TYPE;
import static pub3.war.butterknife_compiler.ButterKnifeProcessor.isSubtypeOfType;

/**
 * A set of all the bindings requested by a single type.
 */
final class BindingSet {
    static final ClassName UTILS = ClassName.get("pub3.war.butterknife.internal", "Utils");
    private static final ClassName VIEW = ClassName.get("android.view", "View");
    private static final ClassName CONTEXT = ClassName.get("android.content", "Context");
    private static final ClassName RESOURCES = ClassName.get("android.content.res", "Resources");
    private static final ClassName UI_THREAD =
            ClassName.get("android.support.annotation", "UiThread");
    private static final ClassName CALL_SUPER =
            ClassName.get("android.support.annotation", "CallSuper");
    private static final ClassName SUPPRESS_LINT =
            ClassName.get("android.annotation", "SuppressLint");
    private static final ClassName UNBINDER = ClassName.get("pub3.war.butterknife", "Unbinder");
    static final ClassName BITMAP_FACTORY = ClassName.get("android.graphics", "BitmapFactory");
    static final ClassName CONTEXT_COMPAT =
            ClassName.get("android.support.v4.content", "ContextCompat");

    private final TypeName targetTypeName;
    private final ClassName bindingClassName;
    private final boolean isFinal;
    private final boolean isView;
    private final boolean isActivity;
    private final boolean isDialog;
    private final ImmutableList<ViewBinding> viewBindings;
    private final ImmutableList<FieldCollectionViewBinding> collectionBindings;
    private final ImmutableList<ResourceBinding> resourceBindings;
    private final BindingSet parentBinding;

    private BindingSet(TypeName targetTypeName, ClassName bindingClassName, boolean isFinal,
                       boolean isView, boolean isActivity, boolean isDialog, ImmutableList<ViewBinding> viewBindings,
                       ImmutableList<FieldCollectionViewBinding> collectionBindings,
                       ImmutableList<ResourceBinding> resourceBindings, BindingSet parentBinding) {
        this.isFinal = isFinal;
        this.targetTypeName = targetTypeName;
        this.bindingClassName = bindingClassName;
        this.isView = isView;
        this.isActivity = isActivity;
        this.isDialog = isDialog;
        this.viewBindings = viewBindings;
        this.collectionBindings = collectionBindings;
        this.resourceBindings = resourceBindings;
        this.parentBinding = parentBinding;
    }

    JavaFile brewJava(int sdk) {
        return JavaFile.builder(bindingClassName.packageName(), createType(sdk))
                .addFileComment("Generated code from Butter Knife. Do not modify!")
                .build();
    }

    private TypeSpec createType(int sdk) {
        TypeSpec.Builder result = TypeSpec.classBuilder(bindingClassName.simpleName())
                .addModifiers(PUBLIC);
        if (isFinal) {
            result.addModifiers(FINAL);
        }

        if (parentBinding != null) {
            result.superclass(parentBinding.bindingClassName);
        } else {
            result.addSuperinterface(UNBINDER);
        }

        if (hasTargetField()) {
            result.addField(targetTypeName, "target", PRIVATE);
        }

        if (isView) {
            result.addMethod(createBindingConstructorForView());
        } else if (isActivity) {
            result.addMethod(createBindingConstructorForActivity());
        } else if (isDialog) {
            result.addMethod(createBindingConstructorForDialog());
        }
        if (!constructorNeedsView()) {
            // Add a delegating constructor with a target type + view signature for reflective use.
            result.addMethod(createBindingViewDelegateConstructor());
        }
        result.addMethod(createBindingConstructor(sdk));

        if (hasViewBindings() || parentBinding == null) {
            result.addMethod(createBindingUnbindMethod(result));
        }

        return result.build();
    }

    private MethodSpec createBindingViewDelegateConstructor() {
        return MethodSpec.constructorBuilder()
                .addJavadoc("@deprecated Use {@link #$T($T, $T)} for direct creation.\n    "
                                + "Only present for runtime invocation through {@code ButterKnife.bind()}.\n",
                        bindingClassName, targetTypeName, CONTEXT)
                .addAnnotation(Deprecated.class)
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC)
                .addParameter(targetTypeName, "target")
                .addParameter(VIEW, "source")
                .addStatement(("this(target, source.getContext())"))
                .build();
    }

    private MethodSpec createBindingConstructorForView() {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC)
                .addParameter(targetTypeName, "target");
        if (constructorNeedsView()) {
            builder.addStatement("this(target, target)");
        } else {
            builder.addStatement("this(target, target.getContext())");
        }
        return builder.build();
    }

    private MethodSpec createBindingConstructorForActivity() {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC)
                .addParameter(targetTypeName, "target");
        if (constructorNeedsView()) {
            builder.addStatement("this(target, target.getWindow().getDecorView())");
        } else {
            builder.addStatement("this(target, target)");
        }
        return builder.build();
    }

    private MethodSpec createBindingConstructorForDialog() {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC)
                .addParameter(targetTypeName, "target");
        if (constructorNeedsView()) {
            builder.addStatement("this(target, target.getWindow().getDecorView())");
        } else {
            builder.addStatement("this(target, target.getContext())");
        }
        return builder.build();
    }

    private MethodSpec createBindingConstructor(int sdk) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC);

        constructor.addParameter(targetTypeName, "target");

        if (constructorNeedsView()) {
            constructor.addParameter(VIEW, "source");
        } else {
            constructor.addParameter(CONTEXT, "context");
        }

        if (hasUnqualifiedResourceBindings()) {
            // Aapt can change IDs out from underneath us, just suppress since all will work at runtime.
            constructor.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "ResourceType")
                    .build());
        }

        if (parentBinding != null) {
            if (parentBinding.constructorNeedsView()) {
                constructor.addStatement("super(target, source)");
            } else if (constructorNeedsView()) {
                constructor.addStatement("super(target, source.getContext())");
            } else {
                constructor.addStatement("super(target, context)");
            }
            constructor.addCode("\n");
        }
        if (hasTargetField()) {
            constructor.addStatement("this.target = target");
            constructor.addCode("\n");
        }

        if (hasViewBindings()) {
            if (hasViewLocal()) {
                // Local variable in which all views will be temporarily stored.
                constructor.addStatement("$T view", VIEW);
            }
            for (ViewBinding binding : viewBindings) {
                addViewBinding(constructor, binding);
            }
            for (FieldCollectionViewBinding binding : collectionBindings) {
                constructor.addStatement("$L", binding.render());
            }

            if (!resourceBindings.isEmpty()) {
                constructor.addCode("\n");
            }
        }

        if (!resourceBindings.isEmpty()) {
            if (constructorNeedsView()) {
                constructor.addStatement("$T context = source.getContext()", CONTEXT);
            }
            if (hasResourceBindingsNeedingResource(sdk)) {
                constructor.addStatement("$T res = context.getResources()", RESOURCES);
            }
            for (ResourceBinding binding : resourceBindings) {
                constructor.addStatement("$L", binding.render(sdk));
            }
        }

        return constructor.build();
    }

    private MethodSpec createBindingUnbindMethod(TypeSpec.Builder bindingClass) {
        MethodSpec.Builder result = MethodSpec.methodBuilder("unbind")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC);
        if (!isFinal && parentBinding == null) {
            result.addAnnotation(CALL_SUPER);
        }

        if (hasTargetField()) {
            if (hasFieldBindings()) {
                result.addStatement("$T target = this.target", targetTypeName);
            }
            result.addStatement("if (target == null) throw new $T($S)", IllegalStateException.class,
                    "Bindings already cleared.");
            result.addStatement("$N = null", hasFieldBindings() ? "this.target" : "target");
            result.addCode("\n");
            for (ViewBinding binding : viewBindings) {
                if (binding.getFieldBinding() != null) {
                    result.addStatement("target.$L = null", binding.getFieldBinding().getName());
                }
            }
            for (FieldCollectionViewBinding binding : collectionBindings) {
                result.addStatement("target.$L = null", binding.name);
            }
        }

        if (parentBinding != null) {
            result.addCode("\n");
            result.addStatement("super.unbind()");
        }
        return result.build();
    }

    private void addFieldAndUnbindStatement(TypeSpec.Builder result, MethodSpec.Builder unbindMethod,
                                            ViewBinding bindings) {
        String fieldName = bindings.isBoundToRoot() ? "viewSource" : "view" + bindings.getId().value;
        result.addField(VIEW, fieldName, PRIVATE);

        // We only need to emit the null check if there are zero required bindings.
        boolean needsNullChecked = bindings.getRequiredBindings().isEmpty();
        if (needsNullChecked) {
            unbindMethod.beginControlFlow("if ($N != null)", fieldName);
        }

        unbindMethod.addStatement("$N = null", fieldName);

        if (needsNullChecked) {
            unbindMethod.endControlFlow();
        }
    }

    private void addViewBinding(MethodSpec.Builder result, ViewBinding binding) {
        FieldViewBinding fieldBinding = binding.getFieldBinding();
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("target.$L = ", fieldBinding.getName());

        boolean requiresCast = requiresCast(fieldBinding.getType());
        if (!requiresCast && !fieldBinding.isRequired()) {
            builder.add("source.findViewById($L)", binding.getId().code);
        } else {
            builder.add("$T.find", UTILS);
            builder.add(fieldBinding.isRequired() ? "RequiredView" : "OptionalView");
            if (requiresCast) {
                builder.add("AsType");
            }
            builder.add("(source, $L", binding.getId().code);
            if (fieldBinding.isRequired() || requiresCast) {
                builder.add(", $S", asHumanDescription(singletonList(fieldBinding)));
            }
            if (requiresCast) {
                builder.add(", $T.class", fieldBinding.getRawType());
            }
            builder.add(")");
        }
        result.addStatement("$L", builder.build());
    }

    static String asHumanDescription(Collection<? extends MemberViewBinding> bindings) {
        Iterator<? extends MemberViewBinding> iterator = bindings.iterator();
        switch (bindings.size()) {
            case 1:
                return iterator.next().getDescription();
            case 2:
                return iterator.next().getDescription() + " and " + iterator.next().getDescription();
            default:
                StringBuilder builder = new StringBuilder();
                for (int i = 0, count = bindings.size(); i < count; i++) {
                    if (i != 0) {
                        builder.append(", ");
                    }
                    if (i == count - 1) {
                        builder.append("and ");
                    }
                    builder.append(iterator.next().getDescription());
                }
                return builder.toString();
        }
    }

    private static TypeName bestGuess(String type) {
        switch (type) {
            case "void":
                return TypeName.VOID;
            case "boolean":
                return TypeName.BOOLEAN;
            case "byte":
                return TypeName.BYTE;
            case "char":
                return TypeName.CHAR;
            case "double":
                return TypeName.DOUBLE;
            case "float":
                return TypeName.FLOAT;
            case "int":
                return TypeName.INT;
            case "long":
                return TypeName.LONG;
            case "short":
                return TypeName.SHORT;
            default:
                int left = type.indexOf('<');
                if (left != -1) {
                    ClassName typeClassName = ClassName.bestGuess(type.substring(0, left));
                    List<TypeName> typeArguments = new ArrayList<>();
                    do {
                        typeArguments.add(WildcardTypeName.subtypeOf(Object.class));
                        left = type.indexOf('<', left + 1);
                    } while (left != -1);
                    return ParameterizedTypeName.get(typeClassName,
                            typeArguments.toArray(new TypeName[typeArguments.size()]));
                }
                return ClassName.bestGuess(type);
        }
    }

    /**
     * True when this type's bindings require a view hierarchy.
     */
    private boolean hasViewBindings() {
        return !viewBindings.isEmpty() || !collectionBindings.isEmpty();
    }

    /**
     * True when this type's bindings use raw integer values instead of {@code R} references.
     */
    private boolean hasUnqualifiedResourceBindings() {
        for (ResourceBinding binding : resourceBindings) {
            if (!binding.id().qualifed) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when this type's bindings use Resource directly instead of Context.
     */
    private boolean hasResourceBindingsNeedingResource(int sdk) {
        for (ResourceBinding binding : resourceBindings) {
            if (binding.requiresResources(sdk)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFieldBindings() {
        for (ViewBinding bindings : viewBindings) {
            if (bindings.getFieldBinding() != null) {
                return true;
            }
        }
        return !collectionBindings.isEmpty();
    }

    private boolean hasTargetField() {
        return hasFieldBindings();
    }

    private boolean hasViewLocal() {
        for (ViewBinding bindings : viewBindings) {
            if (bindings.requiresLocal()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this binding requires a view. Otherwise only a context is needed.
     */
    private boolean constructorNeedsView() {
        return hasViewBindings() //
                || parentBinding != null && parentBinding.constructorNeedsView();
    }

    static boolean requiresCast(TypeName type) {
        return !VIEW_TYPE.equals(type.toString());
    }

    @Override
    public String toString() {
        return bindingClassName.toString();
    }

    static Builder newBuilder(TypeElement enclosingElement) {
        TypeMirror typeMirror = enclosingElement.asType();

        boolean isView = isSubtypeOfType(typeMirror, VIEW_TYPE);
        boolean isActivity = isSubtypeOfType(typeMirror, ACTIVITY_TYPE);
        boolean isDialog = isSubtypeOfType(typeMirror, DIALOG_TYPE);

        TypeName targetType = TypeName.get(typeMirror);
        if (targetType instanceof ParameterizedTypeName) {
            targetType = ((ParameterizedTypeName) targetType).rawType;
        }

        String packageName = getPackage(enclosingElement).getQualifiedName().toString();
        String className = enclosingElement.getQualifiedName().toString().substring(
                packageName.length() + 1).replace('.', '$');
        ClassName bindingClassName = ClassName.get(packageName, className + "_ViewBinding");

        boolean isFinal = enclosingElement.getModifiers().contains(Modifier.FINAL);
        return new Builder(targetType, bindingClassName, isFinal, isView, isActivity, isDialog);
    }

    static final class Builder {
        private final TypeName targetTypeName;
        private final ClassName bindingClassName;
        private final boolean isFinal;
        private final boolean isView;
        private final boolean isActivity;
        private final boolean isDialog;

        private BindingSet parentBinding;

        private final Map<Id, ViewBinding.Builder> viewIdMap = new LinkedHashMap<>();
        private final ImmutableList.Builder<FieldCollectionViewBinding> collectionBindings =
                ImmutableList.builder();
        private final ImmutableList.Builder<ResourceBinding> resourceBindings = ImmutableList.builder();

        private Builder(TypeName targetTypeName, ClassName bindingClassName, boolean isFinal,
                        boolean isView, boolean isActivity, boolean isDialog) {
            this.targetTypeName = targetTypeName;
            this.bindingClassName = bindingClassName;
            this.isFinal = isFinal;
            this.isView = isView;
            this.isActivity = isActivity;
            this.isDialog = isDialog;
        }

        void addField(Id id, FieldViewBinding binding) {
            getOrCreateViewBindings(id).setFieldBinding(binding);
        }

        void addFieldCollection(FieldCollectionViewBinding binding) {
            collectionBindings.add(binding);
        }

        void addResource(ResourceBinding binding) {
            resourceBindings.add(binding);
        }

        void setParent(BindingSet parent) {
            this.parentBinding = parent;
        }

        String findExistingBindingName(Id id) {
            ViewBinding.Builder builder = viewIdMap.get(id);
            if (builder == null) {
                return null;
            }
            FieldViewBinding fieldBinding = builder.fieldBinding;
            if (fieldBinding == null) {
                return null;
            }
            return fieldBinding.getName();
        }

        private ViewBinding.Builder getOrCreateViewBindings(Id id) {
            ViewBinding.Builder viewId = viewIdMap.get(id);
            if (viewId == null) {
                viewId = new ViewBinding.Builder(id);
                viewIdMap.put(id, viewId);
            }
            return viewId;
        }

        BindingSet build() {
            ImmutableList.Builder<ViewBinding> viewBindings = ImmutableList.builder();
            for (ViewBinding.Builder builder : viewIdMap.values()) {
                viewBindings.add(builder.build());
            }
            return new BindingSet(targetTypeName, bindingClassName, isFinal, isView, isActivity, isDialog,
                    viewBindings.build(), collectionBindings.build(), resourceBindings.build(),
                    parentBinding);
        }
    }
}
