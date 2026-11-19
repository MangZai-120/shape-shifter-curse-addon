// Forge 1.20.1 loads this script after Connector remaps Fabric mods.
function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var mixin = 'io/github/apace100/apoli/mixin/integration/connector/PhantomSpawnerMixin';
    var recipe = 'io/github/apace100/apoli/util/ModifiedCraftingRecipe';
    var factory = 'net/minecraft/world/item/crafting/SimpleCraftingRecipeSerializer$Factory';
    var player = 'Lnet/minecraft/server/level/ServerPlayer;';
    var args = '(Lnet/minecraft/server/level/ServerLevel;ZZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;Lnet/minecraft/util/RandomSource;I';
    var vanillaLocals = args + player + ')V';
    var forgeLocals = args + 'Ljava/util/Iterator;' + player + ')V';

    function phantom(node) {
        var capture = null;
        for (var i = 0; i < node.methods.size(); i++) {
            var method = node.methods.get(i);
            if (method.name != 'cachePlayerEntity') continue;
            if (method.desc == forgeLocals) return node;
            if (method.desc == vanillaLocals) capture = method;
        }
        if (capture == null || capture.visibleAnnotations == null) return node;
        // Bind the player by its unique type at this injection point. This works with
        // both the original locals and Forge's extra iterator, which Apoli never uses.
        var code = [];
        for (var insn = capture.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) code.push(insn);
        }
        if (code.length != 4 || code[0].getOpcode() != Opcodes.ALOAD || code[0]['var'] != 0
                || code[1].getOpcode() != Opcodes.ALOAD || code[1]['var'] != 7
                || code[2].getOpcode() != Opcodes.PUTFIELD || code[2].owner != mixin
                || code[2].name != 'apoli$CachedPlayer' || code[2].desc != 'Lnet/minecraft/world/entity/player/Player;'
                || code[3].getOpcode() != Opcodes.RETURN) return node;
        for (var j = 0; j < capture.visibleAnnotations.size(); j++) {
            var injection = capture.visibleAnnotations.get(j);
            if (injection.desc != 'Lorg/spongepowered/asm/mixin/injection/Inject;' || injection.values == null) continue;
            var locals = injection.values.indexOf('locals');
            if (locals < 0 || injection.values.get(locals + 1)[1] != 'CAPTURE_FAILHARD') continue;
            injection.values.remove(locals + 1);
            injection.values.remove(locals);
            capture.desc = vanillaLocals.replace('Lnet/minecraft/util/RandomSource;I', '');
            capture.signature = null;
            capture.localVariables = null;
            capture.parameters = null;
            code[1]['var'] = 5;
            capture.visitParameterAnnotation(4, 'Lcom/llamalad7/mixinextras/sugar/Local;', false).visitEnd();
            ASMAPI.log('INFO', 'SSCA repaired the Apoli Connector phantom player capture');
        }
        return node;
    }

    function crafting(node) {
        for (var i = 0; i < node.methods.size(); i++) {
            var method = node.methods.get(i);
            if (method.name != '<clinit>') continue;
            for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.INVOKEDYNAMIC && insn.name == 'create'
                        && insn.desc == '()L' + factory + ';'
                        && insn.bsm.getOwner() == 'java/lang/invoke/LambdaMetafactory'
                        && insn.bsm.getName() == 'metafactory' && insn.bsmArgs.length == 3
                        && insn.bsmArgs[1].getTag() == Opcodes.H_NEWINVOKESPECIAL
                        && insn.bsmArgs[1].getOwner() == recipe && insn.bsmArgs[1].getName() == '<init>'
                        && insn.bsmArgs[1].getDesc() == '(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/CraftingBookCategory;)V') {
                    insn.name = ASMAPI.mapMethod('m_247316_');
                    ASMAPI.log('INFO', 'SSCA repaired the Apoli Connector crafting serializer factory');
                }
            }
        }
        return node;
    }

    return {
        'apoli_phantom_player_capture': {
            target: {type: 'CLASS', name: mixin.replace(/\//g, '.')},
            transformer: phantom
        },
        'apoli_crafting_recipe_factory': {
            target: {type: 'CLASS', name: recipe.replace(/\//g, '.')},
            transformer: crafting
        }
    };
}
