package com.egologic.mcextremo.client.screen;

import com.egologic.mcextremo.network.SkillTreeNetworking;
import com.egologic.mcextremo.skilltree.Skill;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillTreeScreen extends Screen {
    private static final int NODE_SIZE = 28;
    private static final int TREE_TOP_PADDING = 50;
    private static final int TREE_BOTTOM_PADDING = 42;

    private final int experienceLevel;
    private final Map<String, SkillState> states;
    private double panX = 0.0;
    private double panY = -12.0;
    private double zoom = 1.0;
    private boolean dragging;

    public record SkillState(boolean unlocked, boolean canUnlock, int cost) {
    }

    public SkillTreeScreen(int experienceLevel, Map<String, SkillState> states) {
        super(Text.literal("Arbol de Habilidades"));
        this.experienceLevel = experienceLevel;
        this.states = states;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        renderTreeFrame(context);
        renderTreeCanvas(context, mouseX, mouseY);
        renderHeader(context);
        renderSidePanels(context, mouseX, mouseY);
        if (!hasSidePanels()) {
            renderHoveredTooltip(context, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInsideTree((int) mouseX, (int) mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            Skill hovered = getHoveredSkill((int) mouseX, (int) mouseY);
            if (hovered != null) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(hovered.getId());
                ClientPlayNetworking.send(SkillTreeNetworking.UNLOCK_SKILL, buf);
                return true;
            }
        }

        if (button == 0 || button == 1 || button == 2) {
            dragging = true;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        panX += deltaX / zoom;
        panY += deltaY / zoom;
        clampPan();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isInsideTree((int) mouseX, (int) mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        double beforeX = screenToCanvasX(mouseX);
        double beforeY = screenToCanvasY(mouseY);
        zoom = clamp(zoom + verticalAmount * 0.1, 0.65, 1.75);
        panX = ((mouseX - getTreeCenterX()) / zoom) - beforeX;
        panY = ((mouseY - getTreeCenterY()) / zoom) - beforeY;
        clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            panX = 0.0;
            panY = -12.0;
            zoom = 1.0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderBackground(DrawContext context) {
        context.fill(0, 0, width, height, 0x99090B0F);
    }

    private void renderTreeFrame(DrawContext context) {
        drawFrame(context, getTreeLeft(), getTreeTop(), getTreeRight(), getTreeBottom(),
            0xFF2D2415, 0xFFD69A3E, 0xFF303844);
        context.fill(getTreeLeft() + 8, getTreeTop() + TREE_TOP_PADDING,
            getTreeRight() - 8, getTreeBottom() - TREE_BOTTOM_PADDING, 0xFF28313A);
    }

    private void renderTreeCanvas(DrawContext context, int mouseX, int mouseY) {
        int left = getTreeLeft() + 8;
        int top = getTreeTop() + TREE_TOP_PADDING;
        int right = getTreeRight() - 8;
        int bottom = getTreeBottom() - TREE_BOTTOM_PADDING;

        context.enableScissor(left, top, right, bottom);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(getTreeCenterX(), getTreeCenterY(), 0);
        matrices.scale((float) zoom, (float) zoom, 1.0f);
        matrices.translate(panX, panY, 0);

        renderGrid(context);
        renderBranchLabels(context);
        renderConnectors(context);
        renderNodes(context, mouseX, mouseY);

        matrices.pop();
        context.disableScissor();
    }

    private void renderGrid(DrawContext context) {
        for (int x = -520; x <= 520; x += 40) {
            context.fill(x, -390, x + 1, 350, 0x263F4A53);
        }
        for (int y = -390; y <= 350; y += 40) {
            context.fill(-520, y, 520, y + 1, 0x263F4A53);
        }
    }

    private void renderHeader(DrawContext context) {
        int left = getTreeLeft();
        int top = getTreeTop();
        int unlocked = getUnlockedCount();

        context.drawText(textRenderer, Text.literal("\u00A76\u00A7lArbol de Habilidades"), left + 16, top + 12, 0xFFFFE0A3, false);
        context.drawText(textRenderer, Text.literal("\u00A77Niveles: \u00A7e" + experienceLevel + " \u00A78| \u00A77Progreso: \u00A7a" + unlocked + "/" + Skill.values().length),
            left + 16, top + 25, 0xFFFFFFFF, false);

        int barLeft = left + Math.min(190, Math.max(128, getTreeWidth() / 3));
        int barTop = top + 19;
        int barRight = getTreeRight() - 20;
        if (barRight - barLeft > 40) {
            int progress = Math.max(0, Math.min(barRight - barLeft, (int) ((barRight - barLeft) * (unlocked / (float) Skill.values().length))));
            context.fill(barLeft, barTop, barRight, barTop + 9, 0xFF171D23);
            context.fill(barLeft + 1, barTop + 1, barLeft + progress - 1, barTop + 8, 0xFF65D472);
            context.fill(barLeft, barTop, barRight, barTop + 1, 0xFFD69A3E);
            context.fill(barLeft, barTop + 8, barRight, barTop + 9, 0xFFD69A3E);
        }

        String controls = "Arrastra para moverte  |  Rueda: zoom  |  R: centrar";
        context.drawText(textRenderer, Text.literal("\u00A7f" + controls), left + 16, getTreeBottom() - 26, 0xFFECECEC, false);
        context.drawText(textRenderer, Text.literal("\u00A77Zoom: \u00A7e" + (int) Math.round(zoom * 100) + "%"), getTreeRight() - 78, getTreeBottom() - 26, 0xFFECECEC, false);
    }

    private void renderBranchLabels(DrawContext context) {
        drawCenteredText(context, "\u00A7cVida", -360, -282, 0xFFFF7676);
        drawCenteredText(context, "\u00A7aAgilidad", -240, -282, 0xFF7BE87E);
        drawCenteredText(context, "\u00A79Fuerza", -120, -282, 0xFF8DB4FF);
        drawCenteredText(context, "\u00A7bResistencia", 0, -282, 0xFF73E9F5);
        drawCenteredText(context, "\u00A7dTenacidad", 120, -282, 0xFFFF8DF0);
        drawCenteredText(context, "\u00A76Cazador", 240, -282, 0xFFFFC36A);
        drawCenteredText(context, "\u00A72Supervivencia", 360, -282, 0xFF80E68A);
    }

    private void renderConnectors(DrawContext context) {
        for (Skill skill : Skill.values()) {
            Skill prereq = skill.getPrerequisite();
            if (prereq == null) continue;

            Node from = getNode(prereq);
            Node to = getNode(skill);
            SkillState state = states.get(skill.getId());
            int color = state != null && state.unlocked() ? 0xFF65D472 : state != null && state.canUnlock() ? 0xFFFFCE54 : 0xFF87909A;

            int x1 = from.x() + NODE_SIZE / 2;
            int y1 = from.y() + NODE_SIZE / 2;
            int x2 = to.x() + NODE_SIZE / 2;
            int y2 = to.y() + NODE_SIZE / 2;
            int midY = (y1 + y2) / 2;
            context.fill(x1 - 1, Math.min(y1, midY), x1 + 2, Math.max(y1, midY), color);
            context.fill(Math.min(x1, x2), midY - 1, Math.max(x1, x2), midY + 2, color);
            context.fill(x2 - 1, Math.min(midY, y2), x2 + 2, Math.max(midY, y2), color);
        }
    }

    private void renderNodes(DrawContext context, int mouseX, int mouseY) {
        Skill hoveredSkill = getHoveredSkill(mouseX, mouseY);
        for (Skill skill : Skill.values()) {
            Node node = getNode(skill);
            SkillState state = states.get(skill.getId());
            boolean unlocked = state != null && state.unlocked();
            boolean canUnlock = state != null && state.canUnlock();
            boolean canAfford = state != null && experienceLevel >= state.cost();
            boolean hovered = skill == hoveredSkill;

            int border = unlocked ? 0xFF65D472 : canUnlock && canAfford ? 0xFFFFCE54 : canUnlock ? 0xFFFF7070 : 0xFF87909A;
            int fill = hovered ? 0xFF6A4F2A : unlocked ? 0xFF245436 : 0xFF35404A;
            context.fill(node.x() - 3, node.y() - 3, node.x() + NODE_SIZE + 3, node.y() + NODE_SIZE + 3, 0xFF15191E);
            context.fill(node.x() - 2, node.y() - 2, node.x() + NODE_SIZE + 2, node.y() + NODE_SIZE + 2, border);
            context.fill(node.x(), node.y(), node.x() + NODE_SIZE, node.y() + NODE_SIZE, fill);
            context.drawItem(new ItemStack(skill.getIcon()), node.x() + 6, node.y() + 6);
            if (unlocked) {
                context.drawText(textRenderer, Text.literal("\u00A7a\u2713"), node.x() + 19, node.y() + 19, 0xFFFFFFFF, false);
            } else if (canUnlock && !canAfford) {
                context.drawText(textRenderer, Text.literal("\u00A7c!"), node.x() + 21, node.y() + 19, 0xFFFFFFFF, false);
            }
        }
    }

    private void renderSidePanels(DrawContext context, int mouseX, int mouseY) {
        if (!hasSidePanels()) {
            renderCompactGuide(context);
            return;
        }

        int sideWidth = getSideWidth();
        int left = getTreeLeft() - sideWidth - 8;
        int right = left + sideWidth;
        int top = getTreeTop();
        int bottom = Math.min(getTreeBottom(), top + 174);
        drawFrame(context, left, top, right, bottom, 0xFF172027, 0xFF79A7B5, 0xFF26323A);
        drawSmallTitle(context, "Guia rapida", left + 10, top + 10);
        drawWrapped(context, "1. Desbloquea Base.", left + 10, top + 30, sideWidth - 18, 0xFFFFFFFF);
        drawWrapped(context, "2. Arrastra el arbol para explorar todas las ramas.", left + 10, top + 46, sideWidth - 18, 0xFFFFFFFF);
        drawWrapped(context, "3. Usa la rueda para acercar o alejar.", left + 10, top + 78, sideWidth - 18, 0xFFFFFFFF);
        drawWrapped(context, "4. Verde = desbloqueada, dorado = disponible.", left + 10, top + 110, sideWidth - 18, 0xFFFFFFFF);

        int detailLeft = getTreeRight() + 8;
        int detailRight = detailLeft + sideWidth;
        drawFrame(context, detailLeft, top, detailRight, bottom, 0xFF201B15, 0xFFD69A3E, 0xFF2D2B24);
        Skill hovered = getHoveredSkill(mouseX, mouseY);
        if (hovered == null) {
            drawSmallTitle(context, "Detalle", detailLeft + 10, top + 10);
            drawWrapped(context, "Pasa el mouse por una habilidad para ver requisito, costo y efecto.", detailLeft + 10, top + 32, sideWidth - 18, 0xFFFFFFFF);
            renderLegend(context, detailLeft + 10, top + 92);
        } else {
            renderSkillDetails(context, hovered, detailLeft + 10, top + 10, sideWidth - 18);
        }
    }

    private void renderCompactGuide(DrawContext context) {
        int left = getTreeLeft() + 14;
        int bottom = getTreeBottom() - 26;
        int right = getTreeRight() - 14;
        context.fill(left - 4, bottom - 4, right + 4, bottom + 20, 0xDD28313A);
        drawWrapped(context, "Arrastra para moverte. Rueda = zoom. R = centrar.", left, bottom, right - left, 0xFFFFFFFF);
    }

    private void renderLegend(DrawContext context, int x, int y) {
        legendDot(context, x, y, 0xFF65D472, "Desbloqueada");
        legendDot(context, x, y + 14, 0xFFFFCE54, "Disponible");
        legendDot(context, x, y + 28, 0xFFFF7070, "Falta EXP");
        legendDot(context, x, y + 42, 0xFF87909A, "Bloqueada");
    }

    private void renderSkillDetails(DrawContext context, Skill skill, int x, int y, int width) {
        SkillState state = states.get(skill.getId());
        drawSmallTitle(context, stripFormatting(skill.getDisplayName()), x, y);
        drawWrapped(context, stripFormatting(skill.getDescription()), x, y + 20, width, 0xFFFFFFFF);
        int lineY = y + 56;
        if (skill.getPrerequisite() != null) {
            boolean met = state != null && state.canUnlock();
            drawWrapped(context, "Requisito: " + stripFormatting(skill.getPrerequisite().getDisplayName()), x, lineY, width, met ? 0xFF9DFF9D : 0xFFFF9696);
            lineY += 24;
        }
        int cost = state != null ? state.cost() : skill.getCost();
        drawWrapped(context, "Costo: " + cost + " niveles", x, lineY, width, experienceLevel >= cost ? 0xFF9DFF9D : 0xFFFF9696);
        lineY += 18;
        if (state != null && state.unlocked()) {
            drawWrapped(context, "Estado: desbloqueada", x, lineY, width, 0xFF9DFF9D);
        } else if (state != null && state.canUnlock() && experienceLevel >= cost) {
            drawWrapped(context, "Click para desbloquear", x, lineY, width, 0xFFFFCE54);
        } else {
            drawWrapped(context, "Aun no disponible", x, lineY, width, 0xFFFF9696);
        }
    }

    private void renderHoveredTooltip(DrawContext context, int mouseX, int mouseY) {
        Skill skill = getHoveredSkill(mouseX, mouseY);
        if (skill == null) return;

        SkillState state = states.get(skill.getId());
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(skill.getDisplayName()));
        lines.add(Text.literal(skill.getDescription()));
        lines.add(Text.literal(""));

        if (state != null && state.unlocked()) {
            lines.add(Text.literal("\u00A7aDesbloqueada"));
        } else {
            if (skill.getPrerequisite() != null) {
                lines.add(Text.literal((state != null && state.canUnlock() ? "\u00A7a" : "\u00A7c")
                    + "Requisito: " + skill.getPrerequisite().getDisplayName()));
            }
            int cost = state != null ? state.cost() : skill.getCost();
            lines.add(Text.literal((experienceLevel >= cost ? "\u00A7a" : "\u00A7c") + "Costo: " + cost + " niveles"));
            lines.add(Text.literal("\u00A77Click para desbloquear"));
        }

        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private Skill getHoveredSkill(int mouseX, int mouseY) {
        if (!isInsideTree(mouseX, mouseY)) return null;
        double canvasX = screenToCanvasX(mouseX);
        double canvasY = screenToCanvasY(mouseY);
        for (Skill skill : Skill.values()) {
            Node node = getNode(skill);
            if (canvasX >= node.x() && canvasX < node.x() + NODE_SIZE
                && canvasY >= node.y() && canvasY < node.y() + NODE_SIZE) {
                return skill;
            }
        }
        return null;
    }

    private Node getNode(Skill skill) {
        if (skill == Skill.BASE) return new Node(-NODE_SIZE / 2, 245);

        String id = skill.getId();
        int branchX;
        if (id.startsWith("vida_")) branchX = -360;
        else if (id.startsWith("agilidad_")) branchX = -240;
        else if (id.startsWith("fuerza_")) branchX = -120;
        else if (id.startsWith("resistencia_")) branchX = 0;
        else if (id.startsWith("tenacidad_")) branchX = 120;
        else if (id.startsWith("cazador_")) branchX = 240;
        else if (id.startsWith("supervivencia_")) branchX = 360;
        else branchX = 0;
        return branchNode(branchX, skill.getTier());
    }

    private Node branchNode(int branchX, int tier) {
        return new Node(branchX - NODE_SIZE / 2, 245 - tier * 88);
    }

    private double screenToCanvasX(double screenX) {
        return (screenX - getTreeCenterX()) / zoom - panX;
    }

    private double screenToCanvasY(double screenY) {
        return (screenY - getTreeCenterY()) / zoom - panY;
    }

    private boolean isInsideTree(int mouseX, int mouseY) {
        return mouseX >= getTreeLeft() + 8 && mouseX < getTreeRight() - 8
            && mouseY >= getTreeTop() + TREE_TOP_PADDING && mouseY < getTreeBottom() - TREE_BOTTOM_PADDING;
    }

    private void clampPan() {
        panX = clamp(panX, -560.0, 560.0);
        panY = clamp(panY, -320.0, 220.0);
    }

    private int getTreeLeft() {
        return width / 2 - getTreeWidth() / 2;
    }

    private int getTreeRight() {
        return width / 2 + getTreeWidth() / 2;
    }

    private int getTreeTop() {
        return height < 260 ? 6 : 12;
    }

    private int getTreeBottom() {
        return height - (height < 260 ? 6 : 12);
    }

    private int getTreeWidth() {
        if (hasSidePanels()) {
            return Math.min(600, width - getSideWidth() * 2 - 44);
        }
        return Math.max(300, width - 24);
    }

    private int getTreeCenterX() {
        return (getTreeLeft() + getTreeRight()) / 2;
    }

    private int getTreeCenterY() {
        return (getTreeTop() + TREE_TOP_PADDING + getTreeBottom() - TREE_BOTTOM_PADDING) / 2;
    }

    private boolean hasSidePanels() {
        return width >= 860 && height >= 300;
    }

    private int getSideWidth() {
        return Math.min(190, Math.max(150, (width - 620) / 2 - 14));
    }

    private int getUnlockedCount() {
        int unlocked = 0;
        for (Skill skill : Skill.values()) {
            SkillState state = states.get(skill.getId());
            if (state != null && state.unlocked()) unlocked++;
        }
        return unlocked;
    }

    private void drawFrame(DrawContext context, int left, int top, int right, int bottom, int outer, int border, int inner) {
        context.fill(left, top, right, bottom, outer);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, border);
        context.fill(left + 4, top + 4, right - 4, bottom - 4, inner);
    }

    private void drawSmallTitle(DrawContext context, String title, int x, int y) {
        context.drawText(textRenderer, Text.literal("\u00A76\u00A7l" + title), x, y, 0xFFFFE0A3, false);
    }

    private void drawCenteredText(DrawContext context, String text, int centerX, int y, int color) {
        Text rendered = Text.literal(text);
        context.drawText(textRenderer, rendered, centerX - textRenderer.getWidth(rendered) / 2, y, color, false);
    }

    private void drawWrapped(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        for (var line : textRenderer.wrapLines(Text.literal(text), maxWidth)) {
            context.drawText(textRenderer, line, x, y, color, false);
            y += 11;
        }
    }

    private void legendDot(DrawContext context, int x, int y, int color, String label) {
        context.fill(x, y + 2, x + 8, y + 10, color);
        context.drawText(textRenderer, Text.literal(label), x + 14, y + 2, 0xFFFFFFFF, false);
    }

    private String stripFormatting(String value) {
        return value.replaceAll("\u00A7.", "");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Node(int x, int y) {
    }
}
