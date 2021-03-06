package org.briarproject.android.groups;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.AuthorView;
import org.briarproject.android.util.ElasticHorizontalSpace;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.Author;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.util.StringUtils;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ReadGroupPostActivity extends BriarActivity
implements OnClickListener {

	static final int RESULT_REPLY = RESULT_FIRST_USER;
	static final int RESULT_PREV_NEXT = RESULT_FIRST_USER + 1;

	private static final Logger LOG =
			Logger.getLogger(ReadGroupPostActivity.class.getName());

	private GroupId groupId = null;
	private String groupName = null;
	private long timestamp = -1, minTimestamp = -1;
	private ImageButton prevButton = null, nextButton = null;
	private ImageButton replyButton = null;
	private TextView content = null;
	private int position = -1;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	private volatile MessageId messageId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		groupName = i.getStringExtra("briar.GROUP_NAME");
		if(groupName == null) throw new IllegalStateException();
		setTitle(groupName);
		b = i.getByteArrayExtra("briar.MESSAGE_ID");
		if(b == null) throw new IllegalStateException();
		messageId = new MessageId(b);
		String contentType = i.getStringExtra("briar.CONTENT_TYPE");
		if(contentType == null) throw new IllegalStateException();
		timestamp = i.getLongExtra("briar.TIMESTAMP", -1);
		if(timestamp == -1) throw new IllegalStateException();
		minTimestamp = i.getLongExtra("briar.MIN_TIMESTAMP", -1);
		if(minTimestamp == -1) throw new IllegalStateException();
		position = i.getIntExtra("briar.POSITION", -1);
		if(position == -1) throw new IllegalStateException();
		String authorName = i.getStringExtra("briar.AUTHOR_NAME");
		String s = i.getStringExtra("briar.AUTHOR_STATUS");
		if(s == null) throw new IllegalStateException();
		Author.Status authorStatus = Author.Status.valueOf(s);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		ScrollView scrollView = new ScrollView(this);
		scrollView.setLayoutParams(MATCH_WRAP_1);

		LinearLayout message = new LinearLayout(this);
		message.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		AuthorView author = new AuthorView(this);
		author.setLayoutParams(WRAP_WRAP_1);
		author.init(authorName, authorStatus);
		header.addView(author);

		int pad = LayoutUtils.getPadding(this);

		TextView date = new TextView(this);
		date.setPadding(0, pad, pad, pad);
		date.setText(DateUtils.getRelativeTimeSpanString(this, timestamp));
		header.addView(date);
		message.addView(header);

		if(contentType.equals("text/plain")) {
			// Load and display the message body
			content = new TextView(this);
			content.setPadding(pad, 0, pad, pad);
			message.addView(content);
			loadMessageBody();
		}
		scrollView.addView(message);
		layout.addView(scrollView);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		Resources res = getResources();
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));

		prevButton = new ImageButton(this);
		prevButton.setBackgroundResource(0);
		prevButton.setImageResource(R.drawable.navigation_previous_item);
		prevButton.setOnClickListener(this);
		footer.addView(prevButton);
		footer.addView(new ElasticHorizontalSpace(this));

		nextButton = new ImageButton(this);
		nextButton.setBackgroundResource(0);
		nextButton.setImageResource(R.drawable.navigation_next_item);
		nextButton.setOnClickListener(this);
		footer.addView(nextButton);
		footer.addView(new ElasticHorizontalSpace(this));

		replyButton = new ImageButton(this);
		replyButton.setBackgroundResource(0);
		replyButton.setImageResource(R.drawable.social_reply_all);
		replyButton.setOnClickListener(this);
		footer.addView(replyButton);
		layout.addView(footer);

		setContentView(layout);
	}

	@Override
	public void onPause() {
		super.onPause();
		if(isFinishing()) markMessageRead();
	}

	private void markMessageRead() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					db.setReadFlag(messageId, true);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Marking read took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void loadMessageBody() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					byte[] body = db.getMessageBody(messageId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading message took " + duration + " ms");
					displayMessageBody(StringUtils.fromUtf8(body));
				} catch(NoSuchMessageException e) {
					finishOnUiThread();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayMessageBody(final String body) {
		runOnUiThread(new Runnable() {
			public void run() {
				content.setText(body);
			}
		});
	}

	public void onClick(View view) {
		if(view == prevButton) {
			Intent i = new Intent();
			i.putExtra("briar.POSITION", position - 1);
			setResult(RESULT_PREV_NEXT, i);
			finish();
		} else if(view == nextButton) {
			Intent i = new Intent();
			i.putExtra("briar.POSITION", position + 1);
			setResult(RESULT_PREV_NEXT, i);
			finish();
		} else if(view == replyButton) {
			Intent i = new Intent(this, WriteGroupPostActivity.class);
			i.putExtra("briar.GROUP_ID", groupId.getBytes());
			i.putExtra("briar.GROUP_NAME", groupName);
			i.putExtra("briar.PARENT_ID", messageId.getBytes());
			i.putExtra("briar.MIN_TIMESTAMP", minTimestamp);
			startActivity(i);
			setResult(RESULT_REPLY);
			finish();
		}
	}
}
