package model;

public class Flight {
	private String from;
	private String to;
	private String dateDeparture;
	private String dateReturn;
	private int adult;
	private int child;
	private float price;
	private String cia;

	public Flight() {
	}

	public Flight(String from, String to, String dateDeparture, String dateReturn, int adult, int child) {
		this.setFrom(from);
		this.setTo(to);
		this.setDateDeparture(dateDeparture);
		this.setDateReturn(dateReturn);
		this.setAdult(adult);
		this.setChild(child);
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getDateDeparture() {
		return dateDeparture;
	}

	public void setDateDeparture(String dateDeparture) {
		this.dateDeparture = dateDeparture;
	}

	public String getDateReturn() {
		return dateReturn;
	}

	public void setDateReturn(String dateReturn) {
		this.dateReturn = dateReturn;
	}

	public int getAdult() {
		return adult;
	}

	public void setAdult(int adult) {
		this.adult = adult;
	}

	public int getChild() {
		return child;
	}

	public void setChild(int child) {
		this.child = child;
	}

	public float getPrice() {
		return price;
	}

	public void setPrice(float price) {
		this.price = price;
	}

	public String getCia() {
		return cia;
	}

	public void setCia(String cia) {
		this.cia = cia;
	}

	@Override
	public String toString() {
		return getCia() + ", " + getFrom() + ", " + getTo() + ", " + getDateDeparture() + ", " + getDateReturn() + ", "
				+ getAdult() + ", " + getChild() + ", " + getPrice();
	}
}