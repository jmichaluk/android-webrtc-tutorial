package me.pntutorial.pnrtcblog;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import me.kevingleason.pnwebrtc.PnPeer;
import me.kevingleason.pnwebrtc.PnRTCClient;
import me.kevingleason.pnwebrtc.PnRTCListener;
import me.pntutorial.pnrtcblog.util.Constants;

public class VideoChatActivity extends Activity {

    public static final String VIDEO_TRACK_ID = "videoPN";
    public static final String AUDIO_TRACK_ID = "audioPN";
    public static final String LOCAL_MEDIA_STREAM_ID = "localStreamPN";

    private PnRTCClient pnRTCClient;
    private VideoSource localVideoSource;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private GLSurfaceView mVideoView;

    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_chat);

        // send user back to MainActivity if they did not attach a username to the intent
        Bundle extras = getIntent().getExtras();
        if (extras == null || !extras.containsKey(Constants.USER_NAME)) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            Toast.makeText(this, "Need to pass username to VideoChatActivity in intent extras (Constants.USER_NAME).", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        this.username = extras.getString(Constants.USER_NAME, "");

        // set up global configurations for app
        PeerConnectionFactory.initializeAndroidGlobals(
                this,  // Context
                true,  // Audio enabled
                true,  // Video enabled
                true,  // hardware acceleration enabled
                null); // render egl context

        PeerConnectionFactory pcFactory = new PeerConnectionFactory();
        this.pnRTCClient = new PnRTCClient(Constants.PUB_KEY, Constants.SUB_KEY, this.username);

        // get the number of cams & front/back face device name
        //int camNumber = VideoCapturerAndroid.getDeviceCount();
        String frontFacingCam = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        // String backFacingCam = VideoCapturerAndroid.getNameOfBackFacingDevice();

        // Create instance for the device name
        VideoCapturerAndroid capturer = (VideoCapturerAndroid) VideoCapturerAndroid.create(frontFacingCam);

        // Create a video source
        localVideoSource = pcFactory.createVideoSource(capturer, this.pnRTCClient.videoConstraints());
        // make a video track
        VideoTrack localVideoTrack = pcFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);

        // create an audio source
        AudioSource audioSource = pcFactory.createAudioSource(this.pnRTCClient.audioConstraints());
        // make an audio track
        AudioTrack localAudioTrack = pcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);

        // create media stream
        MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);

        // add tracks
        mediaStream.addTrack(localVideoTrack);
        mediaStream.addTrack(localAudioTrack);

        this.mVideoView = (GLSurfaceView) findViewById(R.id.gl_surface);
        VideoRendererGui.setView(mVideoView, null);

        // get video renderer
        remoteRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
        localRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);


        // Attach the RTC listener to trigger callback events
        this.pnRTCClient.attachRTCListener(new MyRTCListener());
        this.pnRTCClient.attachLocalMediaStream(mediaStream);

        // Listen on a channel. This is your phone number.
        this.pnRTCClient.listenOn(this.username);

        // Set max chat users
        this.pnRTCClient.setMaxConnections(1);

        // auto-connect if Constants.CALL_USER is in the intent extras
        if (extras.containsKey(Constants.JSON_CALL_USER)) {
            String callUser = extras.getString(Constants.JSON_CALL_USER, "");
            this.pnRTCClient.connect(callUser);
        }
    }

    public void hangup(View view) {
        this.pnRTCClient.closeAllConnections();
        startActivity(new Intent(VideoChatActivity.this, MainActivity.class));
    }

    private class MyRTCListener extends PnRTCListener {

        @Override
        public void onLocalStream(final MediaStream localStream) {
            // display local video stream when attached to PnWebRTCClient
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(localStream.videoTracks.size()==0) return;
                    localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
                }
            });
        }

        @Override
        public void onAddRemoteStream(final MediaStream remoteStream, final PnPeer peer) {
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VideoChatActivity.this,"Connected to " + peer.getId(), Toast.LENGTH_SHORT).show();
                    try {
                        if(remoteStream.videoTracks.size()==0) return;

                        // update the size of the renderers
                        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
                        VideoRendererGui.update(remoteRender, 0,0,100,100,VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
                        VideoRendererGui.update(localRender, 72,72,25,25,VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }


        @Override
        public void onPeerConnectionClosed(PnPeer peer) {
            // return to MainActivity on hangup
            Intent intent = new Intent(VideoChatActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }
}

