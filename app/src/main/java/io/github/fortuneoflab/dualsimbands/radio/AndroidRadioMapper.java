package io.github.fortuneoflab.dualsimbands.radio;

import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyDisplayInfo;
import java.util.ArrayList;
import java.util.List;

/** Thin conversion from platform objects; all validation lives in the pure model classes. */
final class AndroidRadioMapper {
    private AndroidRadioMapper() {}

    static RadioSnapshot.Service service(ServiceState state, long now) {
        RadioSnapshot.RegState voiceReg = RadioSnapshot.RegState.fromPlatform(state.getState());
        RadioSnapshot.RegState dataReg = RadioSnapshot.RegState.UNKNOWN;
        Rat voiceRat = Rat.UNKNOWN, dataRat = Rat.UNKNOWN;
        for (NetworkRegistrationInfo nri : state.getNetworkRegistrationInfoList()) {
            if (nri.getTransportType() != AccessNetworkConstants.TRANSPORT_TYPE_WWAN) continue;
            Rat rat = Rat.fromNetworkType(nri.getAccessNetworkTechnology());
            if ((nri.getDomain() & NetworkRegistrationInfo.DOMAIN_PS) != 0) {
                dataRat = nri.isRegistered() ? rat : Rat.UNKNOWN;
                dataReg = nri.isRegistered() ? RadioSnapshot.RegState.IN_SERVICE : RadioSnapshot.RegState.OUT_OF_SERVICE;
            }
            if ((nri.getDomain() & NetworkRegistrationInfo.DOMAIN_CS) != 0 && nri.isRegistered()) voiceRat = rat;
        }
        if (voiceReg == RadioSnapshot.RegState.POWER_OFF) dataReg = RadioSnapshot.RegState.POWER_OFF;
        int channel = state.getChannelNumber();
        return new RadioSnapshot.Service(voiceReg, dataReg, voiceRat, dataRat,
                channel == Measure.UNAVAILABLE || channel < 0 ? -1 : channel, state.getCellBandwidths(), now);
    }

    static RadioSnapshot.Display display(TelephonyDisplayInfo info, long now) {
        return new RadioSnapshot.Display(Rat.fromNetworkType(info.getNetworkType()),
                DisplayOverride.fromPlatform(info.getOverrideNetworkType()), now);
    }

    static RadioSnapshot.Signal signal(SignalStrength strength, long now) {
        Integer rsrp = null, rsrq = null, rssnr = null, rssi = null, ssRsrp = null, ssRsrq = null, ssSinr = null;
        for (CellSignalStrength c : strength.getCellSignalStrengths()) {
            if (c instanceof CellSignalStrengthLte && rsrp == null) {
                CellSignalStrengthLte l = (CellSignalStrengthLte) c;
                rsrp = Measure.valid(l.getRsrp(), -140, -43);
                rsrq = Measure.valid(l.getRsrq(), -34, 3);
                rssnr = Measure.valid(l.getRssnr(), -20, 30);
                rssi = Measure.valid(l.getRssi(), -113, -51);
            } else if (c instanceof CellSignalStrengthNr && ssRsrp == null) {
                CellSignalStrengthNr n = (CellSignalStrengthNr) c;
                ssRsrp = Measure.valid(n.getSsRsrp(), -156, -31);
                ssRsrq = Measure.valid(n.getSsRsrq(), -43, 20);
                ssSinr = Measure.valid(n.getSsSinr(), -23, 40);
            }
        }
        return new RadioSnapshot.Signal(rsrp, rsrq, rssnr, rssi, ssRsrp, ssRsrq, ssSinr, now);
    }

    static RadioSnapshot.Cells cells(List<CellInfo> infos, long now) {
        List<LteCell> lte = new ArrayList<>();
        List<NrCell> nr = new ArrayList<>();
        long newest = -1;
        if (infos != null) for (CellInfo info : infos) {
            long at = info.getTimestampMillis();
            if (at <= 0 || at > now) at = now;
            newest = Math.max(newest, at);
            if (info instanceof CellInfoLte) {
                CellInfoLte c = (CellInfoLte) info;
                CellIdentityLte id = c.getCellIdentity();
                CellSignalStrengthLte sig = c.getCellSignalStrength();
                lte.add(LteCell.fromRaw(c.isRegistered(), c.getCellConnectionStatus(), id.getEarfcn(), id.getBands(),
                        id.getPci(), id.getTac(), id.getCi(), id.getBandwidth(),
                        sig.getRsrp(), sig.getRsrq(), sig.getRssi(), sig.getRssnr(),
                        id.getMccString(), id.getMncString(), sig.getTimingAdvance(), at));
            } else if (info instanceof CellInfoNr) {
                CellInfoNr c = (CellInfoNr) info;
                CellIdentity identity = c.getCellIdentity();
                if (!(identity instanceof CellIdentityNr)) continue;
                CellIdentityNr id = (CellIdentityNr) identity;
                CellSignalStrength s = c.getCellSignalStrength();
                CellSignalStrengthNr sig = s instanceof CellSignalStrengthNr ? (CellSignalStrengthNr) s : null;
                nr.add(NrCell.fromRaw(c.isRegistered(), c.getCellConnectionStatus(), id.getNrarfcn(), id.getBands(),
                        id.getPci(), id.getTac(), id.getNci(),
                        sig == null ? Measure.UNAVAILABLE : sig.getSsRsrp(),
                        sig == null ? Measure.UNAVAILABLE : sig.getSsRsrq(),
                        sig == null ? Measure.UNAVAILABLE : sig.getSsSinr(),
                        id.getMccString(), id.getMncString(),
                        sig != null && android.os.Build.VERSION.SDK_INT >= 34 ? sig.getTimingAdvanceMicros() : Measure.UNAVAILABLE, at));
            }
        }
        return new RadioSnapshot.Cells(RadioSnapshot.Cells.Status.OK, lte, nr, newest < 0 ? now : newest);
    }
}
