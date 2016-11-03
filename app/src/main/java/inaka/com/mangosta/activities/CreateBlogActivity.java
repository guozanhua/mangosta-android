package inaka.com.mangosta.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.pubsub.Subscription;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.jxmpp.jid.Jid;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import inaka.com.mangosta.R;
import inaka.com.mangosta.models.Event;
import inaka.com.mangosta.xmpp.XMPPSession;
import inaka.com.mangosta.xmpp.microblogging.elements.PublishPostExtension;

public class CreateBlogActivity extends BaseActivity {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.createBlogFloatingButton)
    FloatingActionButton createBlogFloatingButton;

    @Bind(R.id.createBlogText)
    EditText createBlogText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_blog);

        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);

        createBlogFloatingButton.setIcon(R.drawable.ic_action_send_dark);
        createBlogFloatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!createBlogText.getText().toString().isEmpty()) {
                    publishBlogPost();
                }
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void publishBlogPost() {
        final ProgressDialog progress = ProgressDialog.show(this, getString(R.string.blog_post_publishing), getString(R.string.loading), true);

        Tasks.executeInBackground(this, new BackgroundWork<Object>() {
            @Override
            public Object doInBackground() throws Exception {
                Jid jid = XMPPSession.getInstance().getXMPPConnection().getUser().asEntityBareJid();

                // create stanza
                PublishPostExtension publishPostExtension = new PublishPostExtension(jid, createBlogText.getText().toString());
                PubSub publishPostPubSub = PubSub.createPubsubPacket(jid, IQ.Type.set, publishPostExtension, null);

                // send stanza
                XMPPSession.getInstance().getXMPPConnection().sendStanza(publishPostPubSub);

                // allow comments
                createNodeToAllowComments(publishPostExtension.getId());

                return null;
            }
        }, new Completion<Object>() {
            @Override
            public void onSuccess(Context context, Object result) {
                progress.dismiss();
                createBlogText.setText("");
                Toast.makeText(CreateBlogActivity.this, getString(R.string.blog_post_published), Toast.LENGTH_SHORT).show();
                EventBus.getDefault().post(new Event(Event.Type.BLOG_POST_CREATED));
                finish();
            }

            @Override
            public void onError(Context context, Exception e) {
                progress.dismiss();
                Toast.makeText(CreateBlogActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }

    private void createNodeToAllowComments(String blogPostId) {
        String nodeName = "urn:xmpp:microblog:0:comments/" + blogPostId;
        PubSubManager pubSubManager = PubSubManager.getInstance(XMPPSession.getInstance().getXMPPConnection());
        try {
            // create node
            LeafNode node = pubSubManager.createNode(nodeName);

            // subscribe to comments
            String myJIDString = XMPPSession.getInstance().getXMPPConnection().getUser().asEntityBareJid().toString();
            Subscription subscription = node.subscribe(myJIDString);

            Log.wtf("Comments subscription state", subscription.getState().toString());
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException | SmackException.NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
