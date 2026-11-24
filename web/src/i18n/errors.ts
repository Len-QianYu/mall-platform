// Backend messages are translated for display; API requests and stored values keep their original form.
export const englishErrors: Record<string, string> = {
  '统计天数需在 1–90 天之间': 'The date range must be between 1 and 90 days',
  '缺少订单 ID': 'Order ID is required',
  '只有已支付的订单才能发货，请刷新后重试':
    'Only paid orders can be shipped. Refresh and try again.',
  '只有待支付的订单才能取消，请刷新后重试':
    'Only unpaid orders can be cancelled. Refresh and try again.',
  '只有已支付、未发货的订单才能退款，请刷新后重试':
    'Only paid, unshipped orders can be refunded. Refresh and try again.',
  商品信息不符合要求: 'Invalid product information',
  商品不存在: 'Product does not exist',
  '请填写有效的商品 ID 和非零库存增减数量':
    'Enter a valid product ID and a nonzero stock adjustment',
  '商品不存在、可用库存不足或调整后库存超出范围':
    'Product not found, insufficient stock or adjustment outside the allowed range',
  '商品 ID 或状态不正确': 'Invalid product ID or status',
  '请检查商品、秒杀价、配额、限购数量和活动时间':
    'Check the product, sale price, allocation, purchase limit and sale dates',
  秒杀活动已创建: 'Flash sale created',
  '活动 ID 不正确': 'Invalid sale ID',
  '活动已关闭，剩余配额已归还普通库存':
    'Sale closed. Remaining stock has been returned to regular inventory.',
  活动不存在或已关闭: 'Sale does not exist or is closed',
  '用户 ID 或状态不正确': 'Invalid user ID or status',
  不能禁用自己: 'You cannot disable your own account',
  用户不存在: 'User does not exist',
  用户信息不符合要求: 'Invalid user information',
  不能修改自己的角色: 'You cannot change your own role',
  商品或数量不正确: 'Invalid product or quantity',
  数量或勾选状态不正确: 'Invalid quantity or selection',
  '购物车里没有这件商品，请刷新后重试':
    'This item is no longer in your cart. Refresh and try again.',
  已更新: 'Updated',
  勾选状态不正确: 'Invalid selection',
  缺少要删除的条目: 'Select the items to remove',
  订单信息不符合要求: 'Invalid order information',
  下单成功: 'Order placed',
  '只有已发货的订单才能确认收货，请刷新后重试':
    'Only shipped orders can be confirmed as received. Refresh and try again.',
  '缺少订单 ID 或支付方式': 'Order ID and payment method are required',
  '商品 ID 或收藏状态不正确': 'Invalid product ID or favorite status',
  '已收藏，将提醒上架和价格变动': 'Saved. You will receive availability and price change alerts.',
  '通知 ID 不正确': 'Invalid notification ID',
  通知不存在: 'Notification does not exist',
  已标为已读: 'Marked as read',
  活动筛选条件不正确: 'Invalid sale filter',
  用户名或密码格式不正确: 'Invalid username or password format',
  '账户已被封禁,无法登录': 'This account is disabled and cannot sign in',
  这是普通用户: 'This is a user account. Choose user sign-in.',
  这是管理员: 'This is an admin account. Choose admin sign-in.',
  退出登录成功: 'Signed out',
  不支持的上传类型: 'Unsupported upload type',
  请选择要上传的图片: 'Select an image to upload',
  '用户不存在，头像保存失败': 'Could not save avatar because the user does not exist',
  用户名或密码不符合要求: 'Invalid username or password',
  用户名已存在: 'This username is already taken',
  注册成功: 'Account created',
  资料不符合要求: 'Invalid profile information',
  '新密码需 6–32 个字符': 'New password must be 6–32 characters',
  当前密码不正确: 'Current password is incorrect',
  密码已修改: 'Password changed',
  文件存储请求失败: 'Could not save or load the file',
  '文件服务暂不可用，请稍后重试': 'File service is unavailable. Please try again later.',
  用户名或密码错误: 'Incorrect username or password',
  '获取支付宝收银台失败，请稍后重试': 'Could not open Alipay checkout. Please try again later.',
  '支付流水缺少支付宝交易号，无法退款':
    'The Alipay transaction ID is missing. This payment cannot be refunded.',
  '支付宝支付未启用，请选择其他支付方式': 'Alipay is unavailable. Choose another payment method.',
  '失败，请稍后重试': 'Failed. Please try again later.',
  '上传到 RustFS 失败': 'Could not upload file',
  '读取 RustFS 文件失败': 'Could not read file',
  '删除 RustFS 文件失败': 'Could not delete file',
  '秒杀商品请单独结算，不能与购物车商品混合下单':
    'Flash sale items must be checked out separately from cart items',
  '该订单正在提交，请稍后重试': 'This order is being submitted. Please wait and try again.',
  '下单请求已中断，请稍后重试': 'Order submission was interrupted. Please try again later.',
  '下单服务暂时不可用，请稍后重试': 'Ordering is temporarily unavailable. Please try again later.',
  '该下单请求号已用于其他订单内容，请重新发起结算':
    'This checkout has changed. Start checkout again.',
  暂不支持该支付方式: 'This payment method is not supported yet',
  '订单不是待支付状态，请刷新后重试':
    'This order is no longer awaiting payment. Refresh and try again.',
  '订单支付已超时，请重新下单': 'The payment deadline has passed. Please place a new order.',
  '订单已有待支付流水，请先查询支付结果或取消订单':
    'A payment is already pending. Check its status or cancel the order first.',
  '暂时无法确认支付宝交易状态，请稍后重试':
    'Could not confirm Alipay payment status. Please try again later.',
  '未找到成功支付流水，无法退款': 'No successful payment was found to refund',
  暂不支持该支付方式退款: 'Refunds are not supported for this payment method yet',
  '订单状态已变化，请刷新后重试': 'Order status has changed. Refresh and try again.',
  支付宝交易与订单金额不匹配: 'The Alipay transaction amount does not match this order',
  支付宝交易号不匹配: 'The Alipay transaction ID does not match',
  结束时间必须晚于开始时间和当前时间:
    'The end time must be later than the start time and the current time',
  每人限购数量不能超过活动总配额: 'The per-customer limit cannot exceed the total sale allocation',
  '商品已下架或普通可用库存不足，无法划拨秒杀配额':
    'The product is unavailable or has insufficient regular stock for this sale',
  秒杀价必须低于商品当前售价: 'The sale price must be lower than the current product price',
  秒杀活动或商品不正确: 'Invalid flash sale or product',
  '秒杀配额不足或活动已结束，请刷新后重试':
    'Insufficient sale stock or the sale has ended. Refresh and try again.',
  秒杀商品已下架: 'This sale product is unavailable',
  已达到本场活动的限购数量: 'You have reached the purchase limit for this sale',
  '秒杀配额记录不一致，暂时无法回补': 'Sale stock could not be restored. Please try again later.',
  秒杀配额回补失败: 'Could not restore sale stock',
  秒杀库存归还失败: 'Could not return sale stock',
  归还秒杀剩余库存失败: 'Could not return remaining sale stock',
  '秒杀服务暂时不可用，请稍后重试':
    'Flash sales are temporarily unavailable. Please try again later.',
  '秒杀商品已抢完，请稍后刷新': 'This sale is sold out. Refresh later to check availability.',
  '秒杀请求不正确，请重新结算': 'Invalid flash sale checkout. Start checkout again.',
  不支持的图片格式: 'Unsupported image format',
}
