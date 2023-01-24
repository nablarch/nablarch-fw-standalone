package nablarch.fw.handler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ハンドラ用テストテーブル
 */
@Entity
@Table(name = "HANDLER_TEST_TABLE")
public class HandlerTestTable {
    
    public HandlerTestTable() {
    }

    public HandlerTestTable(String col1) {
        this.col1 = col1;
    }

    @Id
    @Column(name = "COL1", length = 5, nullable = false)
    public String col1;
}
