GET_INPUT_DATA =
select * from handler_batch_input where status = '0' order by id

UPDATE_STATUS_NORMAL =
update handler_batch_input set status = '1' where id = :id
