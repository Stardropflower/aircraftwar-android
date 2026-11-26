package edu.hitsz.aircraftwar.ui.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * 渐变填充文字的 TextView。
 * <p>
 * 系统 TextView 只支持单一 {@code textColor}，做不出「上面亮、下面略暗」的金属感标题。
 * 这里不重写排版（排版仍然交给 TextView，保住换行、对齐、字距这些行为），
 * 只在 {@code super.onDraw} 之前给 TextPaint 挂一个纵向 {@link LinearGradient} 着色器。
 * <p>
 * 着色器坐标用的是控件本地坐标 {@code (0,0)-(0,height)}，所以文字缩放、换行都不用管，
 * 渐变永远贴合控件高度。着色器在尺寸变化时才重建，不在每帧创建。
 */
public class GradientTextView extends AppCompatTextView {

    private final int topColor;
    private final int bottomColor;
    private LinearGradient shader;
    private int shaderHeight = -1;

    public GradientTextView(Context context) {
        this(context, null);
    }

    public GradientTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // 颜色写死在这里而不是自定义属性：整个项目只有标题用这个效果，
        // 为一个控件加一套 declare-styleable 不划算。
        this.topColor = 0xFFFFFFFF;
        this.bottomColor = 0xFF78C6F0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shader = null;
        shaderHeight = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shader == null || shaderHeight != getHeight()) {
            shaderHeight = getHeight();
            shader = new LinearGradient(0f, 0f, 0f, Math.max(1, shaderHeight),
                    topColor, bottomColor, Shader.TileMode.CLAMP);
            getPaint().setShader(shader);
        }
        super.onDraw(canvas);
    }
}
