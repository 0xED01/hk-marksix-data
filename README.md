# 香港六合彩开奖数据

由 GitHub Actions 定时从香港赛马会公开接口抓取，供「六合彩开奖」App 经 jsDelivr 读取。

- `data/current.json`：最新一期结果和下一期信息
- `data/history.json`：近 30 期结果

开奖当晚 20:47（香港时间）起，每 15 秒检查一次，结果一出立即提交并刷新 CDN 缓存。
