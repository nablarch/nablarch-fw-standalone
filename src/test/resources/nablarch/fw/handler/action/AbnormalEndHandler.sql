UPDATE_STATUS_NORMAL =
update handler_batch_input set status = '1' where id = :id

UPDATE_STATUS_ERROR =
update handler_batch_input set status = '9' where id = :id

