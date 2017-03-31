package pub3.war.butterknifestudy;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import pub3.war.butterknife.ButterKnife;
import pub3.war.butterknife_annotations.BindView;

/**
 * 反射的例子
 */
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.text_view)
    TextView textView;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        toolbar.setTitle("Main");
        textView.setText("test");
    }
}
