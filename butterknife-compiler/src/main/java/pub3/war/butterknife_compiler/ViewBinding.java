package pub3.war.butterknife_compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ViewBinding {
  private final Id id;
  private final FieldViewBinding fieldBinding;

  ViewBinding(Id id,FieldViewBinding fieldBinding) {
    this.id = id;
    this.fieldBinding = fieldBinding;
  }

  public Id getId() {
    return id;
  }

  public FieldViewBinding getFieldBinding() {
    return fieldBinding;
  }


  public List<MemberViewBinding> getRequiredBindings() {
    List<MemberViewBinding> requiredBindings = new ArrayList<>();
    if (fieldBinding != null && fieldBinding.isRequired()) {
      requiredBindings.add(fieldBinding);
    }
    return requiredBindings;
  }


  public boolean requiresLocal() {
    if (isBoundToRoot()) {
      return false;
    }
    return true;
  }

  public boolean isBoundToRoot() {
    return ButterKnifeProcessor.NO_ID.equals(id);
  }

  public static final class Builder {
    private final Id id;

    FieldViewBinding fieldBinding;

    public Builder(Id id) {
      this.id = id;
    }


    public void setFieldBinding(FieldViewBinding fieldBinding) {
      if (this.fieldBinding != null) {
        throw new AssertionError();
      }
      this.fieldBinding = fieldBinding;
    }

    public ViewBinding build() {
      return new ViewBinding(id, fieldBinding);
    }
  }
}
