package me.cortezromeo.inventorypagesplus.support;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reflection bridge to Paper's dialog API. Dialogs exist since Minecraft 1.21.6 and Paper ships
 * the developer API from 1.21.7, so the version is detected by the presence of the classes.
 *
 * The plugin is compiled against Spigot 1.16, so the dialog classes are resolved by name at
 * runtime instead of being linked at build time. When the server does not expose them every
 * call returns false and the caller keeps the plain command flow, so the feature degrades to
 * the old behaviour on older servers instead of failing to load.
 */
public class DialogSupport {

    private static boolean available;
    private static Method deserialize;
    private static Object legacySerializer;
    private static Method dialogCreate;
    private static Method factoryEmpty;
    private static Method entryBase;
    private static Method entryType;
    private static Method baseBuilder;
    private static Method baseBody;
    private static Method baseInputs;
    private static Method baseBuild;
    private static Method bodyPlainMessage;
    private static Method inputText;
    private static Method inputMaxLength;
    private static Method inputBuild;
    private static Method buttonBuilder;
    private static Method buttonAction;
    private static Method buttonBuild;
    private static Method actionCommandTemplate;
    private static Method typeConfirmation;
    private static Method typeMultiAction;
    private static Method multiActionBuild;
    private static Method showDialog;

    static {
        try {
            Class<?> componentClass = load("net.kyori.adventure.text.Component");
            Class<?> serializerClass = load("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            legacySerializer = serializerClass.getMethod("legacySection").invoke(null);
            deserialize = serializerClass.getMethod("deserialize", String.class);

            Class<?> dialogClass = load("io.papermc.paper.dialog.Dialog");
            Class<?> factoryClass = load("io.papermc.paper.registry.RegistryBuilderFactory");
            Class<?> entryBuilderClass = load("io.papermc.paper.registry.data.dialog.DialogRegistryEntry$Builder");
            Class<?> baseClass = load("io.papermc.paper.registry.data.dialog.DialogBase");
            Class<?> baseBuilderClass = load("io.papermc.paper.registry.data.dialog.DialogBase$Builder");
            Class<?> bodyClass = load("io.papermc.paper.registry.data.dialog.body.DialogBody");
            Class<?> buttonClass = load("io.papermc.paper.registry.data.dialog.ActionButton");
            Class<?> buttonBuilderClass = load("io.papermc.paper.registry.data.dialog.ActionButton$Builder");
            Class<?> typeClass = load("io.papermc.paper.registry.data.dialog.type.DialogType");
            Class<?> multiActionBuilderClass = load("io.papermc.paper.registry.data.dialog.type.MultiActionType$Builder");
            Class<?> actionClass = load("io.papermc.paper.registry.data.dialog.action.DialogAction");
            Class<?> inputClass = load("io.papermc.paper.registry.data.dialog.input.DialogInput");
            Class<?> textInputBuilderClass = load("io.papermc.paper.registry.data.dialog.input.TextDialogInput$Builder");

            dialogCreate = dialogClass.getMethod("create", Consumer.class);
            factoryEmpty = factoryClass.getMethod("empty");
            entryBase = entryBuilderClass.getMethod("base", baseClass);
            entryType = entryBuilderClass.getMethod("type", typeClass);
            baseBuilder = baseClass.getMethod("builder", componentClass);
            baseBody = baseBuilderClass.getMethod("body", List.class);
            baseInputs = baseBuilderClass.getMethod("inputs", List.class);
            baseBuild = baseBuilderClass.getMethod("build");
            bodyPlainMessage = bodyClass.getMethod("plainMessage", componentClass);
            inputText = inputClass.getMethod("text", String.class, componentClass);
            inputMaxLength = textInputBuilderClass.getMethod("maxLength", int.class);
            inputBuild = textInputBuilderClass.getMethod("build");
            buttonBuilder = buttonClass.getMethod("builder", componentClass);
            buttonAction = buttonBuilderClass.getMethod("action", actionClass);
            buttonBuild = buttonBuilderClass.getMethod("build");
            actionCommandTemplate = actionClass.getMethod("commandTemplate", String.class);
            typeConfirmation = typeClass.getMethod("confirmation", buttonClass, buttonClass);
            typeMultiAction = typeClass.getMethod("multiAction", List.class);
            multiActionBuild = multiActionBuilderClass.getMethod("build");
            showDialog = findShowDialog();
            available = showDialog != null;
        } catch (Throwable throwable) {
            // Older servers have no dialog API at all, the chat fallback stays in charge.
            DebugManager.debug("DIALOG", "Dialog API unavailable, using the command help: " + throwable);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Opens a menu dialog. Each entry is a label/command pair, so a click runs the very same
     * sub-command a player would type and every check stays in one place.
     */
    public static boolean showMenu(Player player, String title, String body, Map<String, String> actions) {
        if (!available || actions.isEmpty())
            return false;

        try {
            Object base = buildBase(title, body, null);
            List<Object> buttons = new ArrayList<>(actions.size());
            for (Map.Entry<String, String> action : actions.entrySet())
                buttons.add(buildButton(action.getKey(), action.getValue()));

            Object type = multiActionBuild.invoke(typeMultiAction.invoke(null, buttons));
            showDialog.invoke(player, create(base, type));
            return true;
        } catch (Throwable throwable) {
            return fail("menu", throwable);
        }
    }

    /**
     * Opens a confirmation dialog with text inputs. The confirm button runs the command template
     * and the client substitutes every $(key) with the value of the input that has the same key.
     */
    public static boolean showPrompt(Player player, String title, String body, Map<String, String> inputs,
                                     String commandTemplate, String confirmLabel, String cancelLabel) {
        if (!available)
            return false;

        try {
            Object base = buildBase(title, body, inputs);
            Object confirm = buildButton(confirmLabel, commandTemplate);
            // A null action only closes the dialog, which is exactly what "cancel" should do.
            Object cancel = buttonBuild.invoke(buttonBuilder.invoke(null, component(cancelLabel)));
            Object type = typeConfirmation.invoke(null, confirm, cancel);

            showDialog.invoke(player, create(base, type));
            return true;
        } catch (Throwable throwable) {
            return fail("prompt", throwable);
        }
    }

    private static boolean fail(String dialog, Throwable throwable) {
        DebugManager.debug("DIALOG", "Unable to show the " + dialog + " dialog: " + throwable.getMessage());
        return false;
    }

    private static Object create(Object base, Object type) throws Exception {
        Consumer<Object> consumer = factory -> {
            try {
                Object entry = factoryEmpty.invoke(factory);
                entryBase.invoke(entry, base);
                entryType.invoke(entry, type);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        };
        return dialogCreate.invoke(null, consumer);
    }

    private static Object buildBase(String title, String body, Map<String, String> inputs) throws Exception {
        Object builder = baseBuilder.invoke(null, component(title));

        List<Object> bodies = new ArrayList<>(1);
        bodies.add(bodyPlainMessage.invoke(null, component(body)));
        baseBody.invoke(builder, bodies);

        if (inputs != null && !inputs.isEmpty()) {
            List<Object> dialogInputs = new ArrayList<>(inputs.size());
            int maxLength = Math.max(Math.max(1, Settings.PROTECTION_PIN_MIN_LENGTH), Settings.PROTECTION_PIN_MAX_LENGTH);
            for (Map.Entry<String, String> input : inputs.entrySet()) {
                Object inputBuilder = inputText.invoke(null, input.getKey(), component(input.getValue()));
                inputMaxLength.invoke(inputBuilder, maxLength);
                dialogInputs.add(inputBuild.invoke(inputBuilder));
            }
            baseInputs.invoke(builder, dialogInputs);
        }
        return baseBuild.invoke(builder);
    }

    private static Object buildButton(String label, String command) throws Exception {
        Object builder = buttonBuilder.invoke(null, component(label));
        buttonAction.invoke(builder, actionCommandTemplate.invoke(null, command));
        return buttonBuild.invoke(builder);
    }

    private static Object component(String text) throws Exception {
        // addColor already converts every supported hex format into the section-sign form that
        // the legacy serializer understands, so dialogs share the chat colour rules.
        return deserialize.invoke(legacySerializer, InventoryPagesPlus.nms.addColor(text == null ? "" : text));
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        // Initialise nothing while probing: Dialog's static block reads server registries.
        return Class.forName(name, false, DialogSupport.class.getClassLoader());
    }

    private static Method findShowDialog() {
        for (Method method : Player.class.getMethods()) {
            if (method.getName().equals("showDialog") && method.getParameterCount() == 1)
                return method;
        }
        return null;
    }
}
