package net.onixary.shapeShifterCurseFabric.ssc_addon.criteria;

import com.google.gson.JsonObject;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

// 通用：附属形态切换成就触发器。条件可选指定 form_id，匹配则触发
public class OnTransformAddonForm extends AbstractCriterion<OnTransformAddonForm.Condition> {
    public static final Identifier ID = new Identifier("my_addon", "on_transform_addon_form");

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, Identifier formId) {
        trigger(player, condition -> condition.matches(formId));
    }

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        Identifier formId = null;
        if (obj.has("form_id")) {
            formId = new Identifier(obj.get("form_id").getAsString());
        }
        return new Condition(formId);
    }

    public static class Condition extends AbstractCriterionConditions {
        @Nullable
        private final Identifier formId;

        public Condition(@Nullable Identifier formId) {
            super(ID, LootContextPredicate.EMPTY);
            this.formId = formId;
        }

        public boolean matches(Identifier triggered) {
            return formId == null || formId.equals(triggered);
        }
    }
}
