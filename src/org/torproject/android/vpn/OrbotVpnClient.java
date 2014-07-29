/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.torproject.android.vpn;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;

import com.runjva.sourceforge.jsocks.protocol.AuthenticationNone;
import com.runjva.sourceforge.jsocks.protocol.ProxyServer;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.server.ServerAuthenticatorNone;

public class OrbotVpnClient extends Activity implements View.OnClickListener {


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form);


        findViewById(R.id.connect).setOnClickListener(this);
        
        
    }

    @Override
    public void onClick(View v) {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            Intent intent = new Intent(this, OrbotVpnService.class);
            startService(intent);
        }
    }
}
