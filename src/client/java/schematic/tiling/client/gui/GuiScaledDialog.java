package schematic.tiling.client.gui;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;

public abstract class GuiScaledDialog extends GuiBase {

    public GuiScaledDialog() {
        super();
        this.useTitleHierarchy = false;
    }

    protected abstract int getDialogWidth();
    protected abstract int getDialogHeight();

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // Modal dialogs render their own title inside the dialog frame.
        // Suppress GuiBase's default top-left hierarchy title ("Area Editor => Return Cannon Generator")
        // which collides with the dialog frame and header.
    }

    public float getDialogScale() {
        int screenW = super.getScreenWidth();
        int screenH = super.getScreenHeight();
        if (screenW <= 0 || screenH <= 0) {
            return 1.0f;
        }

        // Add 20px padding (10px each side) so the dialog doesn't touch screen edges
        int requiredW = this.getDialogWidth() + 20;
        int requiredH = this.getDialogHeight() + 20;

        float scaleX = (float) screenW / (float) requiredW;
        float scaleY = (float) screenH / (float) requiredH;
        float scale = Math.min(1.0f, Math.min(scaleX, scaleY));

        return Math.max(0.25f, scale);
    }

    @Override
    public int getScreenWidth() {
        float scale = this.getDialogScale();
        return (scale < 0.999f) ? (int) (super.getScreenWidth() / scale) : super.getScreenWidth();
    }

    @Override
    public int getScreenHeight() {
        float scale = this.getDialogScale();
        return (scale < 0.999f) ? (int) (super.getScreenHeight() / scale) : super.getScreenHeight();
    }

    @Override
    public void render(@NotNull GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            drawContext.pose().pushMatrix();
            drawContext.pose().scale(scale, scale);
            int logicalMouseX = (int) (mouseX / scale);
            int logicalMouseY = (int) (mouseY / scale);
            super.render(drawContext, logicalMouseX, logicalMouseY, partialTicks);
            drawContext.renderDeferredElements();
            drawContext.pose().popMatrix();
        } else {
            super.render(drawContext, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean doubleClick) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            MouseButtonEvent scaledClick = new MouseButtonEvent(click.x() / scale, click.y() / scale, click.buttonInfo());
            return super.mouseClicked(scaledClick, doubleClick);
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            MouseButtonEvent scaledClick = new MouseButtonEvent(click.x() / scale, click.y() / scale, click.buttonInfo());
            return super.mouseReleased(scaledClick);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent click, double dragX, double dragY) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            MouseButtonEvent scaledClick = new MouseButtonEvent(click.x() / scale, click.y() / scale, click.buttonInfo());
            return super.mouseDragged(scaledClick, dragX / scale, dragY / scale);
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            return super.mouseScrolled(mouseX / scale, mouseY / scale, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            super.mouseMoved(mouseX / scale, mouseY / scale);
        } else {
            super.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        float scale = this.getDialogScale();
        if (scale < 0.999f) {
            return super.isMouseOver(mouseX / scale, mouseY / scale);
        }
        return super.isMouseOver(mouseX, mouseY);
    }
}

