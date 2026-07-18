package example.progressivestages;

import com.enviouse.progressivestages.common.api.ProgressiveStagesRehaulAPI;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionBehavior;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionProvider;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionResult;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionValueType;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionArgument;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionKind;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadata;
import com.enviouse.progressivestages.common.rehaul.extension.MissingCallbackPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExampleProgressiveStagesPlugin {
    private static final ResourceLocation READY = ResourceLocation.fromNamespaceAndPath("examplemod", "ready");

    private ExampleProgressiveStagesPlugin() {}

    public static void register() {
        ProgressiveStagesRehaulAPI.registerCondition(new ReadyCondition());
        ProgressiveStagesRehaulAPI.registerExtensionMetadata(new ExtensionMetadata(
            READY, ExtensionKind.CONDITION, "Ready for progression",
            "Checks whether the player has reached the configured experience level.",
            "minecraft:experience_bottle",
            List.of(new ExtensionArgument("minimum", "integer", false, 10, null, List.of(),
                "Required experience level.", Map.of("minimum", 0))),
            Set.of("player"), Set.of("level"), Set.of(),
            List.of(Map.of("type", READY.toString(), "minimum", 10)),
            MissingCallbackPolicy.REJECT, false));
    }

    private static final class ReadyCondition implements ConditionProvider {
        @Override public ResourceLocation id() {
            return READY;
        }

        @Override public ConditionValueType valueType() {
            return ConditionValueType.INTEGER;
        }

        @Override public ConditionBehavior behavior() {
            return ConditionBehavior.LIVE_STATE;
        }

        @Override public Set<String> eventInterests() {
            return Set.of("level");
        }

        @Override public Set<SubjectScope> supportedScopes() {
            return Set.of(SubjectScope.PLAYER);
        }

        @Override public ConditionResult evaluate(ConditionNode.Leaf condition, ConditionContext context) {
            int minimum = condition.arguments().get("minimum") instanceof Number number ? number.intValue() : 10;
            int current = context.value("level").filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::intValue).orElse(0);
            return new ConditionResult(current >= minimum, current, minimum,
                current >= minimum ? "Experience requirement met" : "More experience levels are required");
        }
    }
}
