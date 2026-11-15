import com.meteorologia.vehicle.*;
import java.util.*;
public class GeometryDump {
    static String quads(List<VehicleGeometry.Quad> qs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < qs.size(); i++) {
            VehicleGeometry.Quad q = qs.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"m\":\"").append(q.mat).append("\",\"c\":").append(q.color & 0xFFFFFFFFL).append(",\"n\":[").append(q.nx).append(',').append(q.ny).append(',').append(q.nz).append("],\"p\":[");
            for (int k = 0; k < 12; k++) { if (k > 0) sb.append(','); sb.append(q.p[k]); }
            sb.append("]}");
        }
        return sb.append(']').toString();
    }
    static String arr(float[][] a) { StringBuilder sb = new StringBuilder("["); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append('[').append(a[i][0]).append(',').append(a[i][1]).append(',').append(a[i][2]).append(']'); } return sb.append(']').toString(); }
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (VehicleType t : VehicleType.values()) {
            VehicleGeometry.Model m = VehicleGeometry.get(t);
            if (!first) sb.append(','); first = false;
            sb.append('"').append(t.name()).append("\":{\"paint\":").append(m.paint & 0xFFFFFFFFL).append(",\"wheelR\":").append(m.wheelRadius).append(",\"wheelW\":").append(m.wheelHalfWidth)
              .append(",\"quadsBody\":").append(m.body.size()).append(",\"body\":").append(quads(m.body)).append(",\"wheel\":").append(quads(m.wheel)).append(",\"spike\":").append(quads(m.spike))
              .append(",\"wheelPos\":").append(arr(m.wheelPos)).append(",\"spikePos\":").append(arr(m.spikePos)).append('}');
            System.err.println(t + ": body quads=" + m.body.size() + " wheel quads=" + m.wheel.size() + " spike quads=" + m.spike.size());
        }
        System.out.println(sb.append('}'));
    }
}
