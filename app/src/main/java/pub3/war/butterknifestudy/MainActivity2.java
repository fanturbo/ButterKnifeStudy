package pub3.war.butterknifestudy;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.squareup.javapoet.CodeBlock;

import pub3.war.reflectionknife.ContentView;
import pub3.war.reflectionknife.OnClick;
import pub3.war.reflectionknife.ViewInject;
import pub3.war.reflectionknife.ViewInjectUtils;

/**
 * 反射的例子
 */
@ContentView(R.layout.activity_main)
public class MainActivity2 extends AppCompatActivity {
    @ViewInject(R.id.text_view)
    private TextView textView;
    @ViewInject(R.id.toolbar)
    private Toolbar toolbar;

    @OnClick(R.id.fab)
    private void onClick(View view) {
        Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
        CodeBlock.builder().add("");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewInjectUtils.inject(this);
        toolbar.setTitle("Main");
        textView.setText("test");

    }
}
