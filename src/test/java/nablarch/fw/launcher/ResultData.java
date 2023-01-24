package nablarch.fw.launcher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ResultData
 */
@Entity
@Table(name = "ResultData")
public class ResultData {

    public ResultData() {
    }
    
    public ResultData(String id, String activity) {
        this.id = id;
        this.activity = activity;
    }

    @Id
    @Column(name = "ID", length = 3, nullable = false)
    public String id;

    @Id
    @Column(name = "ACTIVITY", length = 32, nullable = false)
    public String activity;
}