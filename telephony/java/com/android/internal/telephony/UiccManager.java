/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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

package com.android.internal.telephony;

import java.util.ArrayList;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.util.Log;

import com.android.internal.telephony.UiccConstants.CardState;
import com.android.internal.telephony.gsm.stk.StkService;
import android.telephony.TelephonyManager;

/* This class will be responsible for keeping all knowledge about
 * ICCs in the system. It will also be used as API to get appropriate
 * applications to pass them to phone and service trackers.
 */
public class UiccManager extends Handler{
    public enum AppFamily {
        APP_FAM_3GPP,
        APP_FAM_3GPP2;
    }

    private static UiccManager mInstance;

    private static final int EVENT_RADIO_ON = 1;
    private static final int EVENT_ICC_STATUS_CHANGED = 2;
    private static final int EVENT_GET_ICC_STATUS_DONE = 3;
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 4;

    private String mLogTag = "RIL_UiccManager";
    CommandsInterface[] mCi;
    Context mContext;
    UiccCard[] mUiccCards;
    private boolean mRadioOn = false;

    private RegistrantList mIccChangedRegistrants = new RegistrantList();
    private StkService mStkService;


    public static UiccManager getInstance(Context c, CommandsInterface[] ci) {
        if (mInstance == null) {
            mInstance = new UiccManager(c, ci);
        } else {
            mInstance.mCi = ci;
            mInstance.mContext = c;
        }
        return mInstance;
    }

    public static UiccManager getInstance(Context c, CommandsInterface ci) {
        if (mInstance == null) {
            mInstance = new UiccManager(c, ci);
        } else {
            mInstance.mCi[0] = ci;
            mInstance.mContext = c;
        }
        return mInstance;
    }

    public static UiccManager getInstance() {
        if (mInstance == null) {
            return null;
        } else {
            return mInstance;
        }
    }

    private UiccManager(Context c, CommandsInterface[] ci) {
        this(c, ci[0]);
    }

    private UiccManager(Context c, CommandsInterface ci) {
        Log.e(mLogTag, "Constructing");
        mUiccCards = new UiccCard[UiccConstants.RIL_MAX_CARDS];

        mCi = new CommandsInterface[TelephonyManager.getPhoneCount()];
        mContext = c;
        mCi[0] = ci;
        mCi[0].registerForOn(this,EVENT_RADIO_ON, null);
        mCi[0].registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
        mCi[0].registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_UNAVAILABLE, null);
        mStkService = StkService.getInstance(mCi[0], null, mContext, null, null);
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;

        switch (msg.what) {
            case EVENT_RADIO_ON:
                mRadioOn = true;
                Log.d(mLogTag, "Radio on -> Forcing sim status update");
                sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
                break;
            case EVENT_ICC_STATUS_CHANGED:
                if (mRadioOn) {
                    Log.d(mLogTag, "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                    mCi[0].getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, msg.obj));
                } else {
                    Log.d(mLogTag, "Received EVENT_ICC_STATUS_CHANGED while radio is not ON. Ignoring");
                }
                break;
            case EVENT_GET_ICC_STATUS_DONE:
                Log.d(mLogTag, " Received EVENT_ICC_STATUS_DONE");
                ar = (AsyncResult)msg.obj;

                onGetIccCardStatusDone(ar);

                //If UiccManager was provided with a callback when icc status update
                //was triggered - now is the time to call it.
                if (ar.userObj != null && ar.userObj instanceof AsyncResult) {
                    AsyncResult internalAr = (AsyncResult)ar.userObj;
                    if (internalAr.userObj != null &&
                            internalAr.userObj instanceof Message) {
                        Message onComplete = (Message)internalAr.userObj;
                        if (onComplete != null) {
                            onComplete.sendToTarget();
                        }
                    }
                } else if (ar.userObj != null && ar.userObj instanceof Message) {
                    Message onComplete = (Message)ar.userObj;
                    onComplete.sendToTarget();
                }
                break;
            case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                mRadioOn = false;
                disposeCards();
                break;
            default:
                Log.e(mLogTag, " Unknown Event " + msg.what);
        }

    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar) {
        if (ar.exception != null) {
            Log.e(mLogTag,"Error getting ICC status. "
                    + "RIL_REQUEST_GET_ICC_STATUS should "
                    + "never return an error", ar.exception);
            return;
        }

        UiccCardStatusResponse status = (UiccCardStatusResponse)ar.result;

        for (int i = 0; i < UiccConstants.RIL_MAX_CARDS; i++) {
            //Update already existing cards
            if (mUiccCards[i] != null && i < status.cards.length) {
                mUiccCards[i].update(status.cards[i], mContext, mCi[0]);
            }

            //Dispose of removed cards
            if (mUiccCards[i] != null && i >= status.cards.length) {
                mUiccCards[i].dispose();
                mUiccCards[i] = null;
            }

            //Create added cards
            if (mUiccCards[i] == null && i < status.cards.length) {
                mUiccCards[i] = new UiccCard(this, i, status.cards[i], mContext, mCi[0]);
            }
        }

        Log.d(mLogTag, "Notifying IccChangedRegistrants");
        mIccChangedRegistrants.notifyRegistrants();
    }

    private synchronized void disposeCards() {
        for (int i = mUiccCards.length - 1; i >= 0; i--) {
            if (mUiccCards[i] != null) {
                Log.d(mLogTag, "Disposing card " + i);
                mUiccCards[i].dispose();
                mUiccCards[i] = null;
            }
        }
    }

    public void triggerIccStatusUpdate(Object onComplete) {
        sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED, onComplete));
    }

    public synchronized UiccCard[] getIccCards() {
        ArrayList<UiccCard> cards = new ArrayList<UiccCard>();
        for (UiccCard c: mUiccCards) {
            //present and absent both cards are returned.
            if (c != null && (c.getCardState() == CardState.PRESENT || c.getCardState() == CardState.ABSENT )) {
                cards.add(c);
            }
        }
        Log.d(mLogTag, "Number of cards = " + cards.size());
        UiccCard arrayCards[] = new UiccCard[cards.size()];
        arrayCards = (UiccCard[])cards.toArray(arrayCards);
        return arrayCards;
    }

    /*
     * This Function gets the UiccCard at the index in case of
     * the card is present and it has any applications or the
     * card is absent.  Otherwise retrun null.
     */
    public synchronized UiccCard getCard(int index) {
        UiccCard card = mUiccCards[index];
        if (card != null &&
            ((card.getCardState() == CardState.PRESENT &&
              card.getNumApplications() > 0) ||
             card.getCardState() == CardState.ABSENT)) {
            return card;
        }
        return null;
    }

    /* Return First subscription of selected family */
    public synchronized UiccCardApplication getCurrentApplication(AppFamily family) {
        for (UiccCard c: mUiccCards) {
            if (c == null || c.getCardState() != CardState.PRESENT) continue;
            int[] subscriptions;
            if (family == AppFamily.APP_FAM_3GPP) {
                subscriptions = c.getSubscription3gppAppIndex();
            } else {
                subscriptions = c.getSubscription3gpp2AppIndex();
            }
            if (subscriptions != null && subscriptions.length > 0) {
                //return First current subscription
                UiccCardApplication app = c.getUiccCardApplication(subscriptions[0]);
                return app;
            } else {
                //No subscriptions found
                return null;
            }
        }
        //No Cards found
        return null;
    }

    //Gets current application based on slotId and appId
    public synchronized UiccCardApplication getApplication(int slotId, int appId) {
        if (slotId >= 0 && slotId < mUiccCards.length &&
            appId >= 0 && appId < mUiccCards.length) {
            UiccCard c = mUiccCards[slotId];
            if (c != null && c.getCardState() == CardState.PRESENT) {
                UiccCardApplication app = c.getUiccCardApplication(appId);
                return app;
            }
        }
        return null;
    }

    //Notifies when any of the cards' STATE changes (or card gets added or removed)
    public void registerForIccChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        synchronized (mIccChangedRegistrants) {
            mIccChangedRegistrants.add(r);
        }
    }
    public void unregisterForIccChanged(Handler h) {
        synchronized (mIccChangedRegistrants) {
            mIccChangedRegistrants.remove(h);
        }
    }
}
